package com.seqscreen;

import com.seqscreen.algo.MotifPattern;
import com.seqscreen.algo.RevComp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApproxMatchTest {

    @Test
    @DisplayName("with no edits it finds exactly what KMP finds")
    void exactEquivalent() {
        assertArrayEquals(new int[]{0, 4}, MotifPattern.positions("ACGTACGT", "ACGT", 0));
    }

    @Test
    @DisplayName("one substitution is found within a budget of one")
    void oneSubstitution() {
        // ACGTT against AACGTA differs by a single base and occurs nowhere exactly.
        int[] hits = MotifPattern.positions("AACGTA", "ACGTT", 1);
        assertTrue(hits.length >= 1, "the site should be reported");
        assertTrue(hits[0] <= 1, Arrays.toString(hits));
    }

    @Test
    @DisplayName("the same substitution is invisible with a budget of zero")
    void substitutionNeedsBudget() {
        assertEquals(0, MotifPattern.positions("AACGTA", "ACGTT", 0).length);
    }

    @Test
    @DisplayName("deletion and insertion both count as one edit")
    void indels() {
        // Overlapping alignments are all valid, so the check is that the site is reported.
        assertTrue(MotifPattern.positions("AACGTA", "ACGTT", 1).length >= 1,
                "deletion of one base should be found");
        assertTrue(MotifPattern.positions("AACGTTTA", "ACGTT", 1).length >= 1,
                "insertion of one base should be found");
    }

    @Test
    @DisplayName("N in the motif matches any base")
    void wildcardInMotif() {
        int[] hits = MotifPattern.positions("ACGTACGT", "ACNT", 0);
        assertEquals(2, hits.length, Arrays.toString(hits));
    }

    @Test
    @DisplayName("N in the sequence matches any motif base when tolerance is on")
    void wildcardInText() {
        assertEquals(1, MotifPattern.positions("ACNTA", "ACGTA", 1).length);
    }

    @Test
    @DisplayName("agrees with a dynamic-programming baseline on random cases")
    void agreesWithDynamicProgramming() {
        SplittableRandom random = new SplittableRandom(11);
        char[] bases = {'A', 'C', 'G', 'T'};
        for (int trial = 0; trial < 500; trial++) {
            StringBuilder text = new StringBuilder();
            int n = 20 + random.nextInt(60);
            for (int i = 0; i < n; i++) {
                text.append(bases[random.nextInt(4)]);
            }
            StringBuilder motif = new StringBuilder();
            int m = 2 + random.nextInt(7);
            for (int i = 0; i < m; i++) {
                motif.append(bases[random.nextInt(4)]);
            }
            int edits = random.nextInt(3);
            int[] myers = MotifPattern.positions(text.toString(), motif.toString(), edits);
            assertConsistentWithDp(text.toString(), motif.toString(), edits, myers, trial);
        }
    }

    @Test
    @DisplayName("a motif longer than one machine word is rejected")
    void motifTooLong() {
        String motif = "A".repeat(65);
        Executable call = () -> MotifPattern.positions("ACGT", motif, 0);
        assertThrows(IllegalArgumentException.class, call);
    }

    @Test
    @DisplayName("reverse complement of a known sequence")
    void reverseComplement() {
        assertEquals("ACGT", RevComp.of("ACGT"));
        assertEquals("TTAC", RevComp.of("GTAA"));
    }

    /**
     * Checked against the textbook recurrence rather than a second copy of Myers: for every
     * column where the optimal free-start alignment costs at most k edits, Myers must report a
     * nearby start, and every Myers hit must sit near such a column.
     */
    private void assertConsistentWithDp(String text, String motif, int edits, int[] myers, int trial) {
        int n = text.length();
        int m = motif.length();
        int[] distance = new int[m + 1];
        int[] start = new int[m + 1];
        for (int i = 0; i <= m; i++) {
            distance[i] = i;
        }
        boolean[] columnHasHit = new boolean[n];
        int[] columnStart = new int[n];
        for (int j = 0; j < n; j++) {
            int[] nextDistance = new int[m + 1];
            int[] nextStart = new int[m + 1];
            nextDistance[0] = 0;
            nextStart[0] = j + 1;
            for (int i = 1; i <= m; i++) {
                boolean same = motif.charAt(i - 1) == text.charAt(j)
                        || motif.charAt(i - 1) == 'N' || text.charAt(j) == 'N';
                int diagonal = distance[i - 1] + (same ? 0 : 1);
                int deleteText = distance[i] + 1;
                int insertText = nextDistance[i - 1] + 1;
                if (diagonal <= deleteText && diagonal <= insertText) {
                    nextDistance[i] = diagonal;
                    nextStart[i] = start[i - 1];
                } else if (deleteText <= insertText) {
                    nextDistance[i] = deleteText;
                    nextStart[i] = start[i];
                } else {
                    nextDistance[i] = insertText;
                    nextStart[i] = nextStart[i - 1];
                }
            }
            distance = nextDistance;
            start = nextStart;
            if (j >= m - 1 && distance[m] <= edits) {
                columnHasHit[j] = true;
                columnStart[j] = start[m];
            }
        }

        for (int hit : myers) {
            boolean covered = false;
            for (int j = 0; j < n && !covered; j++) {
                if (columnHasHit[j] && Math.abs(columnStart[j] - hit) <= edits) {
                    covered = true;
                }
            }
            assertTrue(covered, "trial " + trial + ": Myers hit " + hit + " has no DP support in "
                    + text + " for " + motif + " k=" + edits);
        }
        for (int j = m - 1; j < n; j++) {
            if (!columnHasHit[j]) {
                continue;
            }
            boolean covered = false;
            for (int hit : myers) {
                if (Math.abs(hit - columnStart[j]) <= edits) {
                    covered = true;
                    break;
                }
            }
            assertTrue(covered, "trial " + trial + ": DP alignment ending at " + j
                    + " was missed by Myers in " + text + " for " + motif + " k=" + edits);
        }
    }
}
