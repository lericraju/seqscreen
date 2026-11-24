package com.seqscreen.algo;

/**
 * Reverse complement. DNA is double stranded, so a motif can be present on either strand.
 * A hit on the reverse strand of a sequence is the same physical site as the forward-strand
 * hit of the complement motif, which is why searching the reverse complement and reporting
 * once is enough.
 */
public final class RevComp {

    private static final char[] COMPLEMENT = new char[128];

    static {
        java.util.Arrays.fill(COMPLEMENT, '\0');
        COMPLEMENT['A'] = 'T';
        COMPLEMENT['C'] = 'G';
        COMPLEMENT['G'] = 'C';
        COMPLEMENT['T'] = 'A';
        COMPLEMENT['N'] = 'N';
    }

    private RevComp() {
    }

    public static String of(String sequence) {
        int n = sequence.length();
        char[] out = new char[n];
        for (int i = 0; i < n; i++) {
            char c = sequence.charAt(n - 1 - i);
            char comp = c < 128 ? COMPLEMENT[c] : '\0';
            out[i] = comp == '\0' ? c : comp;
        }
        return new String(out);
    }
}
