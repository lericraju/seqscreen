package com.seqscreen.service;

import com.seqscreen.algo.MotifPattern;
import com.seqscreen.dto.BenchmarkRequest;
import com.seqscreen.dto.BenchmarkResponse;
import com.seqscreen.dto.BenchmarkRow;
import com.seqscreen.index.SequenceIndex;
import com.seqscreen.index.SequenceRecord;
import com.seqscreen.service.SearchOptions.Hit;
import com.seqscreen.validation.ApiException;
import com.seqscreen.validation.MotifValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares the four strategies on growing datasets built from the same generator seed, so the
 * only thing that changes between rows is size. Every row also checks that all four strategies
 * report the same hits: a speed-up that changes the answer is not a speed-up.
 */
@Service
public class BenchmarkService {

    private static final int MAX_SIZES = 5;
    private static final int MAX_SEQUENCES = 200_000;
    private static final long MAX_BASES_PER_SIZE = 60_000_000L;

    private final SearchService searchService;

    public BenchmarkService(SearchService searchService) {
        this.searchService = searchService;
    }

    public BenchmarkResponse run(BenchmarkRequest request) {
        BenchmarkRequest req = request == null
                ? new BenchmarkRequest(null, null, null, null, null, null) : request;
        List<Integer> sizes = req.sizes() == null || req.sizes().isEmpty()
                ? List.of(1000, 10_000, 100_000) : req.sizes();
        int length = req.length() == null ? 500 : req.length();
        String motif = req.motif() == null || req.motif().isBlank()
                ? DatasetService.DEFAULT_MOTIF : req.motif();
        double planted = req.plantedFraction() == null ? DatasetService.DEFAULT_PLANTED_FRACTION
                : req.plantedFraction();
        int k = req.k() == null ? DatasetService.DEFAULT_K : req.k();
        double p = req.p() == null ? DatasetService.DEFAULT_P : req.p();

        MotifValidator.validate(motif, 0);
        MotifValidator.validateK(k);
        MotifValidator.validateP(p);
        if (sizes.size() > MAX_SIZES) {
            throw ApiException.badRequest("At most " + MAX_SIZES + " sizes per benchmark.");
        }
        if (length < 50 || length > 5_000) {
            throw ApiException.badRequest("Sequence length must be between 50 and 5,000 bases.");
        }
        for (int size : sizes) {
            if (size < 10 || size > MAX_SEQUENCES) {
                throw ApiException.badRequest("Each size must be between 10 and "
                        + MAX_SEQUENCES + " sequences.");
            }
            if ((long) size * length > MAX_BASES_PER_SIZE) {
                throw ApiException.badRequest(size + " sequences x " + length
                        + " bases is over the " + MAX_BASES_PER_SIZE + " base benchmark limit.");
            }
        }
        List<Integer> ordered = new ArrayList<>(sizes);
        ordered.sort(Integer::compareTo);

        boolean repetitive = isRepetitive(motif);
        SearchOptions options = new SearchOptions(0, false, 1);
        List<BenchmarkRow> rows = new ArrayList<>(ordered.size());
        List<String> notes = new ArrayList<>();

        boolean warmed = false;
        for (int size : ordered) {
            long buildStart = System.nanoTime();
            List<SequenceRecord> records = DatasetService.synthesize(size, length, 42L, planted, motif);
            double buildMs = (System.nanoTime() - buildStart) / 1_000_000.0;

            long indexStart = System.nanoTime();
            SequenceIndex index = SequenceIndex.build(records, k, p);
            double indexBuildMs = (System.nanoTime() - indexStart) / 1_000_000.0;

            if (!warmed) {
                searchService.run(index, motif, SearchMode.KMP, options);
                warmed = true;
            }

            long t = System.nanoTime();
            SearchOutcome naive = searchService.run(index, motif, SearchMode.NAIVE, options);
            double naiveMs = (System.nanoTime() - t) / 1_000_000.0;

            t = System.nanoTime();
            SearchOutcome kmp = searchService.run(index, motif, SearchMode.KMP, options);
            double kmpMs = (System.nanoTime() - t) / 1_000_000.0;

            t = System.nanoTime();
            SearchOutcome bloom = searchService.run(index, motif, SearchMode.BLOOM_KMP, options);
            double bloomMs = (System.nanoTime() - t) / 1_000_000.0;

            t = System.nanoTime();
            SearchOutcome indexed = searchService.run(index, motif, SearchMode.INDEX_KMP, options);
            double indexMs = (System.nanoTime() - t) / 1_000_000.0;

            boolean identical = signature(naive).equals(signature(kmp))
                    && signature(kmp).equals(signature(bloom))
                    && signature(bloom).equals(signature(indexed));

            String note = "";
            if (!index.indexBuilt()) {
                note = "Index skipped: " + index.indexNote();
                notes.add(size + " sequences: index skipped, so INDEX_KMP degraded to a full KMP scan. "
                        + index.indexNote());
            }

            rows.add(new BenchmarkRow(size, (long) size * length, buildMs, indexBuildMs, naiveMs,
                    kmpMs, bloomMs, indexMs,
                    indexMs <= 0 ? 0 : round(kmpMs / indexMs), index.bloomBytes(), index.indexBytes(),
                    (long) size * length, indexed.confirmedSequences(), indexed.candidates(),
                    indexed.falsePositives(), identical, !index.indexBuilt(), note));
        }

        notes.add("Single run per strategy on a warm JVM, single threaded, forward strand only, "
                + "so the rows measure the algorithms rather than the scheduler.");
        notes.add("Build and index build time are reported separately and excluded from every "
                + "search measurement.");
        if (repetitive) {
            notes.add("This motif is low complexity, so occurrences overlap heavily and every "
                    + "strategy reports many hits. Preprocessing pays off less on repetitive text.");
        }
        notes.add("Bloom filter memory is the sum of one filter per sequence at p=" + p
                + "; a miss in any sequence is a guaranteed absence, a hit still needs confirmation.");

        return new BenchmarkResponse(motif, k, p, length, repetitive, rows, notes);
    }

    private String signature(SearchOutcome outcome) {
        StringBuilder builder = new StringBuilder(outcome.hits().size() * 24);
        for (Hit hit : outcome.hits()) {
            builder.append(hit.sequenceIndex()).append(':').append(hit.position())
                    .append(':').append(hit.strand()).append(';');
        }
        return builder.toString();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** Low complexity: a run of four identical bases, or a period of three or less. */
    private static boolean isRepetitive(String motif) {
        if (MotifPattern.hasWildcard(motif)) {
            return true;
        }
        int run = 1;
        for (int i = 1; i < motif.length(); i++) {
            run = motif.charAt(i) == motif.charAt(i - 1) ? run + 1 : 1;
            if (run >= 4) {
                return true;
            }
        }
        for (int period = 1; period <= 3 && period < motif.length(); period++) {
            boolean periodic = true;
            for (int i = period; i < motif.length(); i++) {
                if (motif.charAt(i) != motif.charAt(i - period)) {
                    periodic = false;
                    break;
                }
            }
            if (periodic) {
                return true;
            }
        }
        return false;
    }
}
