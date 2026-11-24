package com.seqscreen.algo;

/**
 * Bloom filter (Unit 6, CO5). A bit array plus k independent hash functions. Adding a key sets
 * every one of its bits; a lookup answers "possibly contained" as soon as one bit is clear, so
 * the false-negative rate is exactly zero for anything ever added. False positives are possible
 * and their rate is bounded by the sizing formula below.
 *
 * <p>Sizing: for n keys and target false-positive rate p, the optimal bit count is
 * {@code m = -n ln(p) / (ln 2)^2} and the optimal number of hashes is {@code k = (m/n) ln 2}.
 * Hash indices are produced by double hashing, {@code h1 + i*h2 mod m}, which needs only one
 * mix per key and still behaves like k independent hashes.
 */
public final class BloomFilter {

    private static final long SEED = 0x9E3779B97F4A7C15L;

    private final long[] bits;
    private final int bitCount;
    private final int hashCount;

    public BloomFilter(int expectedKeys, double falsePositiveRate) {
        this.bitCount = optimalBits(expectedKeys, falsePositiveRate);
        this.hashCount = optimalHashes(bitCount, expectedKeys);
        this.bits = new long[(bitCount + 63) >>> 6];
    }

    public static int optimalBits(long expectedKeys, double p) {
        if (expectedKeys <= 0) {
            return 64;
        }
        double rate = Math.min(Math.max(p, 1e-9), 0.5);
        double bits = -(expectedKeys * Math.log(rate)) / (Math.log(2) * Math.log(2));
        return (int) Math.max(64, Math.min(Math.round(bits), 1L << 28));
    }

    public static int optimalHashes(int bits, long expectedKeys) {
        if (expectedKeys <= 0) {
            return 1;
        }
        double k = ((double) bits / expectedKeys) * Math.log(2);
        return (int) Math.max(1, Math.min(Math.round(k), 16));
    }

    public void add(long key) {
        long h1 = mix(key);
        long h2 = mix(key ^ SEED) | 1L;
        for (int i = 0; i < hashCount; i++) {
            int index = (int) Long.remainderUnsigned(h1 + (long) i * h2, bitCount);
            bits[index >>> 6] |= 1L << (index & 63);
        }
    }

    public void addAll(long[] keys) {
        for (long key : keys) {
            add(key);
        }
    }

    public boolean mightContain(long key) {
        long h1 = mix(key);
        long h2 = mix(key ^ SEED) | 1L;
        for (int i = 0; i < hashCount; i++) {
            int index = (int) Long.remainderUnsigned(h1 + (long) i * h2, bitCount);
            if ((bits[index >>> 6] & (1L << (index & 63))) == 0L) {
                return false;
            }
        }
        return true;
    }

    /** True only when every key is possibly present; each miss is a guaranteed absence. */
    public boolean mightContainAll(long[] keys) {
        for (long key : keys) {
            if (!mightContain(key)) {
                return false;
            }
        }
        return true;
    }

    public int bitCount() {
        return bitCount;
    }

    public int hashCount() {
        return hashCount;
    }

    public long byteSize() {
        return (long) bits.length * Long.BYTES;
    }

    /** Fraction of bits set, useful for checking the filter against its design target. */
    public double fillRatio() {
        long set = 0;
        for (long word : bits) {
            set += Long.bitCount(word);
        }
        return (double) set / bitCount;
    }

    /** splitmix64 finaliser: cheap, well-distributed, and deterministic across runs. */
    private static long mix(long z) {
        z += SEED;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
