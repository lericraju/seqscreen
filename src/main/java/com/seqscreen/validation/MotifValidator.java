package com.seqscreen.validation;

import com.seqscreen.algo.MotifPattern;

import java.util.Locale;

/**
 * Normalises and checks a motif. Every search path depends on the motif being upper-case
 * A/C/G/T/N, so a bad character is rejected here rather than producing a report full of misses.
 */
public final class MotifValidator {

    /** Exact search has no bit-vector limit, but a motif far longer than this is a mistake. */
    public static final int MAX_EXACT = 512;

    private MotifValidator() {
    }

    public static String validate(String rawMotif, int maxEdits) {
        if (rawMotif == null || rawMotif.isBlank()) {
            throw ApiException.badRequest("Enter a motif to search for.");
        }
        String motif = rawMotif.strip().toUpperCase(Locale.ROOT);
        for (int i = 0; i < motif.length(); i++) {
            char c = motif.charAt(i);
            if (c != 'A' && c != 'C' && c != 'G' && c != 'T' && c != 'N') {
                throw ApiException.badRequest("'" + c + "' is not a DNA base. Use A, C, G, T or N.");
            }
        }
        boolean approximate = maxEdits > 0 || MotifPattern.hasWildcard(motif);
        if (approximate && motif.length() > MotifPattern.MAX_MOTIF) {
            throw ApiException.badRequest("Approximate and wildcard search support motifs up to "
                    + MotifPattern.MAX_MOTIF + " bases; this one is " + motif.length()
                    + ". Search it exactly instead.");
        }
        if (!approximate && motif.length() > MAX_EXACT) {
            throw ApiException.badRequest("Motifs are limited to " + MAX_EXACT + " bases.");
        }
        return motif;
    }

    public static void validateEdits(int maxEdits) {
        if (maxEdits < 0) {
            throw ApiException.badRequest("Allowed edits cannot be negative.");
        }
        if (maxEdits > 5) {
            throw ApiException.badRequest("At most 5 edits are allowed; beyond that nearly every "
                    + "position matches and the result says nothing.");
        }
    }

    public static void validateThreads(int threads) {
        if (threads < 1 || threads > 32) {
            throw ApiException.badRequest("Workers must be between 1 and 32.");
        }
    }

    public static void validateK(int k) {
        if (k < 1 || k > 32) {
            throw ApiException.badRequest("k must be between 1 and 32.");
        }
    }

    public static void validateP(double p) {
        if (p <= 0 || p >= 1) {
            throw ApiException.badRequest("The false-positive target must be between 0 and 1, "
                    + "for example 0.01.");
        }
    }
}
