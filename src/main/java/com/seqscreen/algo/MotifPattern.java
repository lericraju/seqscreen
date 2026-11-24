package com.seqscreen.algo;

/**
 * Myers' bit-vector algorithm: approximate matching in O(text length) regardless of the number
 * of allowed edits, as long as the motif fits in one machine word (64 bases).
 *
 * <p>Each row of the classical dynamic-programming matrix is one bit vector; the carry trick
 * updates a whole row in a handful of word operations. The score carried across columns is the
 * edit distance of the best suffix alignment ending at the current column, so a score within
 * the budget is an occurrence ending there.
 *
 * <p>Wildcards: an {@code N} in the motif sets that position's bit for every base, so it matches
 * anything. An {@code N} in the text matches every motif position when edit tolerance is on or
 * the motif itself contains a wildcard.
 */
public final class MotifPattern {

    public static final int MAX_MOTIF = 64;

    private MotifPattern() {
    }

    public static int[] positions(String text, String motif, int maxEdits) {
        if (text == null || motif == null) {
            throw new IllegalArgumentException("text and motif are required");
        }
        int n = text.length();
        int m = motif.length();
        if (m == 0 || m > MAX_MOTIF) {
            throw new IllegalArgumentException("motif length must be between 1 and " + MAX_MOTIF);
        }
        if (maxEdits < 0) {
            throw new IllegalArgumentException("allowed edits cannot be negative");
        }
        if (m > n) {
            return new int[0];
        }

        long[] peq = new long[128];
        long allOnes = m == 64 ? -1L : (1L << m) - 1L;
        boolean motifWildcard = false;
        for (int i = 0; i < m; i++) {
            char c = motif.charAt(i);
            long bit = 1L << i;
            if (c == 'N') {
                motifWildcard = true;
                peq['A'] |= bit;
                peq['C'] |= bit;
                peq['G'] |= bit;
                peq['T'] |= bit;
            } else {
                peq[c] |= bit;
            }
        }
        boolean textWildcard = motifWildcard || maxEdits > 0;
        long highBit = 1L << (m - 1);

        long pv = allOnes;
        long mv = 0L;
        int score = m;
        IntList hits = new IntList();
        for (int j = 0; j < n; j++) {
            char c = text.charAt(j);
            long eq;
            if (c == 'N') {
                eq = textWildcard ? allOnes : 0L;
            } else {
                eq = (c < 128 ? peq[c] : 0L) & allOnes;
            }

            long xv = eq | mv;
            long xh = (((eq & pv) + pv) ^ pv) | eq;
            long ph = mv | ~(xh | pv);
            long mh = pv & xh;

            if ((ph & highBit) != 0L) {
                score++;
            } else if ((mh & highBit) != 0L) {
                score--;
            }
            // No `| 1` here: seeding the low bit advertises a free start at every column,
            // which walks the score upward instead of letting it return to zero after a hit.
            ph = (ph << 1) & allOnes;
            mh = (mh << 1) & allOnes;
            pv = (mh | ~(xv | ph)) & allOnes;
            mv = ph & xv;

            if (j >= m - 1 && score <= maxEdits) {
                hits.add(j - m + 1);
            }
        }
        return hits.toArray();
    }

    /** True when the motif contains a wildcard base and therefore cannot be screened exactly. */
    public static boolean hasWildcard(String motif) {
        return motif.indexOf('N') >= 0;
    }
}
