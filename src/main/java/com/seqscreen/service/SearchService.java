package com.seqscreen.service;

import com.seqscreen.algo.KmpMatcher;
import com.seqscreen.algo.MotifPattern;
import com.seqscreen.algo.NaiveMatcher;
import com.seqscreen.algo.RevComp;
import com.seqscreen.index.SequenceIndex;
import com.seqscreen.service.SearchOptions.Hit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs a query over a dataset: screen, split the survivors across workers, confirm exactly,
 * then merge in dataset order so the result never depends on thread scheduling.
 */
@Service
public class SearchService {

    /** A motif common enough to hit this many times is a pattern, not a list worth scrolling. */
    public static final int MATCH_LIMIT = 2000;

    private static final int[] NONE = new int[0];

    public SearchOutcome run(SequenceIndex index, String motif, SearchMode mode, SearchOptions options) {
        int maxEdits = options.maxEdits();
        boolean wildcard = MotifPattern.hasWildcard(motif);
        String reverseMotif = options.bothStrands() ? RevComp.of(motif) : null;

        SequenceIndex.Candidates candidates = mode.screens()
                ? index.candidates(motif, options.bothStrands(), maxEdits, mode == SearchMode.INDEX_KMP)
                : null;
        int[] scan = candidates != null ? candidates.sequenceIndices() : index.allSequences();

        int workers = Threads.forWork(options.threads(), scan.length);
        long started = System.nanoTime();
        List<Chunk> chunks = execute(index, scan, motif, reverseMotif, mode, maxEdits, wildcard, workers);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;

        List<Hit> hits = new ArrayList<>();
        int confirmed = 0;
        boolean truncated = false;
        for (Chunk chunk : chunks) {
            confirmed += chunk.confirmed;
            for (Hit hit : chunk.hits) {
                if (hits.size() >= MATCH_LIMIT) {
                    truncated = true;
                    break;
                }
                hits.add(hit);
            }
            if (truncated) {
                break;
            }
        }

        boolean screened = candidates != null && candidates.screened();
        int screenedOut = screened ? index.size() - scan.length : 0;
        int falsePositives = screened ? Math.max(0, scan.length - confirmed) : 0;
        String note = candidates != null ? candidates.note() : "Every sequence was searched.";
        boolean indexUsed = screened && mode == SearchMode.INDEX_KMP && index.indexBuilt();

        return new SearchOutcome(hits, scan.length, screenedOut, confirmed, falsePositives,
                screened, note, indexUsed, workers, elapsedMs, index.size());
    }

    private List<Chunk> execute(SequenceIndex index, int[] scan, String motif, String reverseMotif,
                                SearchMode mode, int maxEdits, boolean wildcard, int workers) {
        if (scan.length == 0) {
            return List.of(new Chunk(List.of(), 0));
        }
        int parts = Math.max(1, workers * 2);
        if (workers == 1 || scan.length < 32) {
            return List.of(scanRange(index, scan, 0, scan.length, motif, reverseMotif, mode, maxEdits, wildcard));
        }
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Chunk>> futures = new ArrayList<>(parts);
            for (int p = 0; p < parts; p++) {
                int from = (int) ((long) scan.length * p / parts);
                int to = (int) ((long) scan.length * (p + 1) / parts);
                if (from >= to) {
                    continue;
                }
                futures.add(pool.submit(() -> scanRange(index, scan, from, to, motif, reverseMotif,
                        mode, maxEdits, wildcard)));
            }
            List<Chunk> chunks = new ArrayList<>(futures.size());
            for (Future<Chunk> future : futures) {
                chunks.add(future.get());
            }
            return chunks;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Search interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof OutOfMemoryError) {
                throw new IllegalStateException("Not enough memory to finish this search.", cause);
            }
            throw new IllegalStateException("Search worker failed: " + cause.getMessage(), cause);
        } finally {
            pool.shutdown();
        }
    }

    private Chunk scanRange(SequenceIndex index, int[] scan, int from, int to, String motif,
                            String reverseMotif, SearchMode mode, int maxEdits, boolean wildcard) {
        List<Hit> hits = new ArrayList<>();
        int confirmed = 0;
        int[] prefix = exactPrefix(mode, maxEdits, wildcard) ? KmpMatcher.prefix(motif) : null;
        int[] reversePrefix = reverseMotif != null && exactPrefix(mode, maxEdits, wildcard)
                ? KmpMatcher.prefix(reverseMotif) : null;
        int length = motif.length();

        for (int k = from; k < to; k++) {
            int sequenceIndex = scan[k];
            String sequence = index.bases(sequenceIndex);
            if (sequence.length() < length) {
                continue;
            }
            int[] forward = positions(sequence, motif, mode, maxEdits, wildcard, prefix);
            int[] reverse = reverseMotif == null ? NONE
                    : positions(sequence, reverseMotif, mode, maxEdits, wildcard, reversePrefix);
            if (forward.length == 0 && reverse.length == 0) {
                continue;
            }
            confirmed++;
            for (int position : forward) {
                hits.add(hit(index, sequenceIndex, sequence, position, length, "forward", maxEdits));
            }
            // A palindromic motif matches both strands at the same coordinate; report it once.
            Set<Integer> seen = forward.length > 8 ? new HashSet<>() : null;
            if (seen != null) {
                for (int position : forward) {
                    seen.add(position);
                }
            }
            for (int position : reverse) {
                boolean duplicate = seen != null ? seen.contains(position) : contains(forward, position);
                if (!duplicate) {
                    hits.add(hit(index, sequenceIndex, sequence, position, length, "reverse", maxEdits));
                }
            }
        }
        return new Chunk(hits, confirmed);
    }

    private boolean exactPrefix(SearchMode mode, int maxEdits, boolean wildcard) {
        return mode != SearchMode.NAIVE && maxEdits == 0 && !wildcard;
    }

    private int[] positions(String sequence, String motif, SearchMode mode, int maxEdits,
                            boolean wildcard, int[] prefix) {
        if (maxEdits > 0 || wildcard) {
            return MotifPattern.positions(sequence, motif, maxEdits);
        }
        if (mode == SearchMode.NAIVE) {
            return NaiveMatcher.positions(sequence, motif);
        }
        return KmpMatcher.positions(sequence, motif, prefix);
    }

    private Hit hit(SequenceIndex index, int sequenceIndex, String sequence, int position,
                    int length, String strand, int maxEdits) {
        int contextStart = Math.max(0, position - SearchOptions.CONTEXT);
        int contextEnd = Math.min(sequence.length(), position + length + SearchOptions.CONTEXT);
        return new Hit(sequenceIndex, index.id(sequenceIndex), position, length, strand, maxEdits,
                sequence.substring(contextStart, contextEnd), contextStart);
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }

    private record Chunk(List<Hit> hits, int confirmed) {
    }
}
