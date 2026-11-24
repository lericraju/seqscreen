package com.seqscreen.algo;

/**
 * Brute-force exact matching. Kept as the baseline every other strategy is measured against:
 * it is the only matcher whose running time is obviously proportional to the work in the
 * worst case, because it retries from the next text position after every mismatch.
 */
public final class NaiveMatcher {

    private NaiveMatcher() {
    }

    public static int[] positions(String text, String motif) {
        if (text == null || motif == null) {
            throw new IllegalArgumentException("text and motif are required");
        }
        int n = text.length();
        int m = motif.length();
        if (m == 0 || m > n) {
            return new int[0];
        }
        IntList hits = new IntList();
        int last = n - m;
        for (int i = 0; i <= last; i++) {
            int j = 0;
            while (j < m && text.charAt(i + j) == motif.charAt(j)) {
                j++;
            }
            if (j == m) {
                hits.add(i);
            }
        }
        return hits.toArray();
    }

    public static int count(String text, String motif) {
        return positions(text, motif).length;
    }
}
