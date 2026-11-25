package com.seqscreen.index;

import com.seqscreen.algo.BloomFilter;
import com.seqscreen.algo.KmerEncoder;
import com.seqscreen.algo.MotifPattern;
import com.seqscreen.algo.RevComp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An immutable collection of sequences plus the two optional screening structures built over it:
 * the inverted k-mer index and one Bloom filter per sequence.
 *
 * <p>Both filters answer "possibly contains the motif" and are always followed by an exact check,
 * so neither can drop a real match. They differ in the cost of the trade: the index is a single
 * shared structure with an up-front build cost and a hard size cap, while the Bloom filters are
 * cheap to build and need no lookup table but must be probed once per sequence.
 */
public final class SequenceIndex {

    /** A dataset larger than this cannot be indexed in the memory a single query is worth. */
    public static final int MAX_INDEXED_OCCURRENCES = 16_000_000;

    /** Probes taken from a motif: enough to be selective without touching every k-mer. */
    private static final int PROBES = 3;

    private final String[] ids;
    private final String[] bases;
    private final int k;
    private final double p;
    private final long totalBases;

    private final KmerIndex index;
    private final BloomFilter[] blooms;
    private final String indexNote;
    private final String bloomNote;

    private SequenceIndex(String[] ids, String[] bases, int k, double p, long totalBases,
                          KmerIndex index, BloomFilter[] blooms, String indexNote, String bloomNote) {
        this.ids = ids;
        this.bases = bases;
        this.k = k;
        this.p = p;
        this.totalBases = totalBases;
        this.index = index;
        this.blooms = blooms;
        this.indexNote = indexNote;
        this.bloomNote = bloomNote;
    }

    public static SequenceIndex build(List<SequenceRecord> records, int k, double p) {
        int n = records.size();
        String[] ids = new String[n];
        String[] bases = new String[n];
        long total = 0;
        long occurrences = 0;
        for (int i = 0; i < n; i++) {
            SequenceRecord record = records.get(i);
            ids[i] = record.id();
            bases[i] = record.bases();
            total += record.bases().length();
            if (record.bases().length() >= k) {
                occurrences += record.bases().length() - k + 1L;
            }
        }

        String indexNote;
        KmerIndex index = null;
        if (k > KmerEncoder.MAX_K) {
            indexNote = "Index skipped: k=" + k + " exceeds the " + KmerEncoder.MAX_K
                    + "-base limit for packed k-mer keys.";
        } else if (k < 1) {
            indexNote = "Index skipped: k must be at least 1.";
        } else if (occurrences > MAX_INDEXED_OCCURRENCES) {
            indexNote = "Index skipped: " + String.format("%,d", occurrences)
                    + " k-mer occurrences exceed the " + String.format("%,d", MAX_INDEXED_OCCURRENCES)
                    + " cap, so this dataset is screened with Bloom filters instead.";
        } else if (occurrences == 0) {
            indexNote = "Index skipped: no sequence is at least k bases long.";
        } else {
            // Distinct k-mers can never exceed 4^k, so sizing the table by occurrences alone
            // would reserve one slot per base and waste hundreds of megabytes.
            long possibleDistinct = k >= 16 ? Integer.MAX_VALUE : Math.min(occurrences, 1L << (2 * k));
            index = new KmerIndex((int) Math.min(possibleDistinct, Integer.MAX_VALUE / 2), (int) occurrences);
            for (int i = 0; i < n; i++) {
                String seq = bases[i];
                if (seq.length() < k) {
                    continue;
                }
                for (int j = 0; j + k <= seq.length(); j++) {
                    long code = KmerEncoder.encode(seq, j, k);
                    if (code >= 0) {
                        index.add(code, i);
                    }
                }
            }
            indexNote = "Index built over " + String.format("%,d", index.distinct())
                    + " distinct " + k + "-mers in " + String.format("%,d", index.occurrences())
                    + " occurrences.";
        }

        String bloomNote;
        BloomFilter[] blooms = null;
        if (k < 1) {
            bloomNote = "Bloom filters skipped: k must be at least 1.";
        } else if (n == 0) {
            bloomNote = "No sequences loaded.";
        } else {
            blooms = new BloomFilter[n];
            long keys = 0;
            for (int i = 0; i < n; i++) {
                int expected = Math.max(1, bases[i].length() - k + 1);
                blooms[i] = new BloomFilter(expected, p);
                long[] codes = KmerEncoder.encodeAll(bases[i], k);
                blooms[i].addAll(codes);
                keys += codes.length;
            }
            bloomNote = "Bloom filters sized for " + String.format("%,d", keys)
                    + " k-mers at a " + p + " target false-positive rate.";
        }

        return new SequenceIndex(ids, bases, k, p, total, index, blooms, indexNote, bloomNote);
    }

    public int size() {
        return ids.length;
    }

    public int k() {
        return k;
    }

    public double falsePositiveRate() {
        return p;
    }

    public long totalBases() {
        return totalBases;
    }

    public String id(int sequence) {
        return ids[sequence];
    }

    public String bases(int sequence) {
        return bases[sequence];
    }

    public int[] allSequences() {
        int[] all = new int[ids.length];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    public boolean indexBuilt() {
        return index != null;
    }

    public boolean bloomBuilt() {
        return blooms != null;
    }

    public String indexNote() {
        return indexNote;
    }

    public String bloomNote() {
        return bloomNote;
    }

    public long indexBytes() {
        return index == null ? 0 : index.memoryBytes();
    }

    public long bloomBytes() {
        if (blooms == null) {
            return 0;
        }
        long bytes = 0;
        for (BloomFilter bloom : blooms) {
            bytes += bloom.byteSize();
        }
        return bytes;
    }

    public int distinctKmers() {
        return index == null ? 0 : index.distinct();
    }

    /**
     * Sequences that could contain the motif. Returns {@code screened == false} when no exact
     * filter applies, in which case every sequence must be searched.
     */
    public Candidates candidates(String motif, boolean bothStrands, int maxEdits, boolean preferIndex) {
        if (maxEdits > 0) {
            return Candidates.all(ids.length,
                    "Approximate search is not screened: an edit anywhere in the motif changes every "
                            + "k-mer it contains, so no exact filter can rule a sequence out.");
        }
        if (MotifPattern.hasWildcard(motif)) {
            return Candidates.all(ids.length,
                    "Wildcard motif is not screened: N matches any base, so the motif's k-mers are "
                            + "not exact strings and no filter can rule a sequence out.");
        }
        if (motif.length() < k) {
            return Candidates.all(ids.length,
                    "Motif is shorter than k=" + k + ", so screening is skipped and every sequence "
                            + "is searched.");
        }
        if (preferIndex && index != null) {
            return indexCandidates(motif, bothStrands);
        }
        if (blooms != null) {
            return bloomCandidates(motif, bothStrands);
        }
        if (index != null) {
            return indexCandidates(motif, bothStrands);
        }
        return Candidates.all(ids.length, "No screening structure is available for this dataset.");
    }

    private Candidates indexCandidates(String motif, boolean bothStrands) {
        boolean[] keep = new boolean[ids.length];
        String reverse = bothStrands ? RevComp.of(motif) : null;
        int probes = collectProbes(motif, reverse, keep);
        int[] out = collect(keep);
        String note = "Screened with " + probes + " rare " + k + "-mer"
                + (probes == 1 ? "" : "s") + " from the index: " + String.format("%,d", out.length)
                + " of " + String.format("%,d", ids.length) + " sequences survived.";
        return new Candidates(out, true, note);
    }

    private int collectProbes(String motif, String reverse, boolean[] keep) {
        int probes = 0;
        probes += probe(motif, keep);
        if (reverse != null) {
            probes += probe(reverse, keep);
        }
        return probes;
    }

    /**
     * Marks every sequence holding one of the motif's rarest k-mers. Within a strand any one k-mer
     * is a valid test, so the postings are unioned: that keeps a superset of what intersecting them
     * would. Across the two strands a union is required, because a hit may be on either.
     */
    private int probe(String motif, boolean[] keep) {
        int windows = motif.length() - k + 1;
        if (windows <= 0) {
            return 0;
        }
        long[] codes = new long[windows];
        int valid = 0;
        for (int i = 0; i < windows; i++) {
            long code = KmerEncoder.encode(motif, i, k);
            if (code >= 0) {
                codes[valid++] = code;
            }
        }
        if (valid == 0) {
            return 0;
        }
        Integer[] order = new Integer[valid];
        for (int i = 0; i < valid; i++) {
            order[i] = i;
        }
        final long[] masked = Arrays.copyOf(codes, valid);
        Arrays.sort(order, (a, b) -> Integer.compare(index.postingCount(masked[a]),
                index.postingCount(masked[b])));
        int taken = 0;
        for (int i = 0; i < valid && taken < PROBES; i++) {
            int[] postings = index.postings(masked[order[i]]);
            if (postings.length == 0) {
                // A k-mer no sequence contains rules the motif out entirely.
                continue;
            }
            for (int sequence : postings) {
                keep[sequence] = true;
            }
            taken++;
        }
        return taken;
    }

    private Candidates bloomCandidates(String motif, boolean bothStrands) {
        String reverse = bothStrands ? RevComp.of(motif) : null;
        long[] forward = KmerEncoder.encodeAll(motif, k);
        long[] backward = reverse == null ? null : KmerEncoder.encodeAll(reverse, k);
        if (forward.length == 0 && (backward == null || backward.length == 0)) {
            return Candidates.all(ids.length, "Motif has no encodable k-mers, so it was not screened.");
        }
        boolean[] keep = new boolean[ids.length];
        int kept = 0;
        for (int i = 0; i < ids.length; i++) {
            BloomFilter bloom = blooms[i];
            boolean hit = forward.length > 0 && bloom.mightContainAll(forward);
            if (!hit && backward != null && backward.length > 0) {
                hit = bloom.mightContainAll(backward);
            }
            if (hit) {
                keep[i] = true;
                kept++;
            }
        }
        String note = "Screened with Bloom filters: " + String.format("%,d", kept) + " of "
                + String.format("%,d", ids.length) + " sequences passed a "
                + p + " target false-positive rate.";
        return new Candidates(collect(keep), true, note);
    }

    private int[] collect(boolean[] keep) {
        int count = 0;
        for (boolean flag : keep) {
            if (flag) {
                count++;
            }
        }
        int[] out = new int[count];
        int at = 0;
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                out[at++] = i;
            }
        }
        return out;
    }

    /** The result of screening: which sequences to check, and how the decision was made. */
    public record Candidates(int[] sequenceIndices, boolean screened, String note) {

        static Candidates all(int total, String note) {
            int[] all = new int[total];
            for (int i = 0; i < total; i++) {
                all[i] = i;
            }
            return new Candidates(all, false, note);
        }
    }

    /** Kept for callers that want the k-mers of a sequence without encoding them again. */
    public long[] kmersOf(int sequence) {
        return KmerEncoder.encodeAll(bases[sequence], k);
    }

    /** Ids in dataset order, used by the benchmark to report what it built. */
    public List<String> sampleIds(int limit) {
        List<String> sample = new ArrayList<>(Math.min(limit, ids.length));
        for (int i = 0; i < Math.min(limit, ids.length); i++) {
            sample.add(ids[i]);
        }
        return sample;
    }
}
