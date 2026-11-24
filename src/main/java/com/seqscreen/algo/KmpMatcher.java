package com.seqscreen.algo;

/**
 * Knuth-Morris-Pratt exact matching (Unit 4). The prefix table records, for every prefix of
 * the motif, the length of its longest proper prefix that is also a suffix. On a mismatch we
 * resume from that length instead of restarting at the next text character, so the text is
 * never re-read: O(n + m) time, O(m) space, reporting every overlapping occurrence.
 */
public final class KmpMatcher {

    private KmpMatcher() {
    }

    /** prefix[i] = length of the longest proper prefix of motif[0..i] that is also its suffix. */
    public static int[] prefix(String motif) {
        int m = motif.length();
        int[] prefix = new int[m];
        int len = 0;
        for (int i = 1; i < m; i++) {
            while (len > 0 && motif.charAt(i) != motif.charAt(len)) {
                len = prefix[len - 1];
            }
            if (motif.charAt(i) == motif.charAt(len)) {
                len++;
            }
            prefix[i] = len;
        }
        return prefix;
    }

    public static int[] positions(String text, String motif) {
        return positions(text, motif, null);
    }

    /** Overload for callers that search the same motif over many sequences. */
    public static int[] positions(String text, String motif, int[] prefixTable) {
        if (text == null || motif == null) {
            throw new IllegalArgumentException("text and motif are required");
        }
        int n = text.length();
        int m = motif.length();
        if (m == 0 || m > n) {
            return new int[0];
        }
        int[] prefix = prefixTable != null ? prefixTable : prefix(motif);
        IntList hits = new IntList();
        int matched = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            while (matched > 0 && c != motif.charAt(matched)) {
                matched = prefix[matched - 1];
            }
            if (c == motif.charAt(matched)) {
                matched++;
            }
            if (matched == m) {
                hits.add(i - m + 1);
                matched = prefix[matched - 1];
            }
        }
        return hits.toArray();
    }

    public static int count(String text, String motif) {
        return positions(text, motif).length;
    }
}
