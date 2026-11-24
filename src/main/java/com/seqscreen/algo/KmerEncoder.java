package com.seqscreen.algo;

/**
 * Two-bit k-mer encoding: A=00, C=01, G=10, T=11, packed into a long, most significant base
 * first. A k-mer therefore fits in 2k bits, which caps k at 32 for a long and, in practice,
 * at {@link #MAX_K} so the hash keys stay cheap to mix. Any N or unknown base has no encoding,
 * so callers get -1 and fall back to a full scan for that sequence.
 */
public final class KmerEncoder {

    /** Widest k the index supports; beyond this the keys lose too many distinct values. */
    public static final int MAX_K = 16;

    private static final int[] CODE = new int[128];

    static {
        java.util.Arrays.fill(CODE, -1);
        CODE['A'] = 0;
        CODE['C'] = 1;
        CODE['G'] = 2;
        CODE['T'] = 3;
    }

    private KmerEncoder() {
    }

    /** Encodes the k-mer starting at {@code offset}, or returns -1 if it contains anything but ACGT. */
    public static long encode(String sequence, int offset, int k) {
        long code = 0L;
        for (int i = 0; i < k; i++) {
            char c = sequence.charAt(offset + i);
            int value = c < 128 ? CODE[c] : -1;
            if (value < 0) {
                return -1L;
            }
            code = (code << 2) | value;
        }
        return code;
    }

    /** Every encodable k-mer of the sequence, in order. N-containing windows are skipped. */
    public static long[] encodeAll(String sequence, int k) {
        int n = sequence.length();
        if (k <= 0 || k > n) {
            return new long[0];
        }
        long[] out = new long[n - k + 1];
        int count = 0;
        for (int i = 0; i + k <= n; i++) {
            long code = encode(sequence, i, k);
            if (code >= 0) {
                out[count++] = code;
            }
        }
        return count == out.length ? out : java.util.Arrays.copyOf(out, count);
    }

    public static boolean encodable(String sequence, int offset, int k) {
        return encode(sequence, offset, k) >= 0;
    }
}
