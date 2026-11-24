package com.seqscreen;

import com.seqscreen.algo.KmpMatcher;
import com.seqscreen.algo.NaiveMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KmpMatcherTest {

    @Test
    @DisplayName("every overlapping occurrence is reported")
    void overlappingOccurrences() {
        assertArrayEquals(new int[]{0, 1, 2}, KmpMatcher.positions("AAAA", "AA"));
    }

    @Test
    @DisplayName("prefix table of ABAB is 0,0,1,2")
    void prefixTable() {
        assertArrayEquals(new int[]{0, 0, 1, 2}, KmpMatcher.prefix("ABAB"));
    }

    @Test
    @DisplayName("prefix table of a one-character motif is 0")
    void prefixOfSingleCharacter() {
        assertArrayEquals(new int[]{0}, KmpMatcher.prefix("A"));
    }

    @Test
    @DisplayName("matches at the very start and the very end")
    void boundaryPositions() {
        int[] hits = KmpMatcher.positions("ACGTACGT", "ACGT");
        assertArrayEquals(new int[]{0, 4}, hits);
    }

    @Test
    @DisplayName("a motif longer than the text finds nothing")
    void motifLongerThanText() {
        assertEquals(0, KmpMatcher.count("ACG", "ACGTACGT"));
    }

    @Test
    @DisplayName("empty text finds nothing")
    void emptyText() {
        assertEquals(0, KmpMatcher.count("", "ACGT"));
    }

    @Test
    @DisplayName("single base motif counts every exact base")
    void singleBaseMotif() {
        assertEquals(3, KmpMatcher.count("ACGTAA", "A"));
    }

    @Test
    @DisplayName("no occurrence reports an empty array")
    void absentMotif() {
        assertEquals(0, KmpMatcher.count("ACGTACGT", "TTTT"));
    }

    @Test
    @DisplayName("agrees with the naive matcher on random text")
    void agreesWithNaive() {
        SplittableRandom random = new SplittableRandom(7);
        char[] bases = {'A', 'C', 'G', 'T'};
        for (int trial = 0; trial < 300; trial++) {
            StringBuilder text = new StringBuilder();
            int n = 20 + random.nextInt(200);
            for (int i = 0; i < n; i++) {
                text.append(bases[random.nextInt(4)]);
            }
            StringBuilder motif = new StringBuilder();
            int m = 1 + random.nextInt(Math.min(12, n));
            for (int i = 0; i < m; i++) {
                motif.append(bases[random.nextInt(4)]);
            }
            assertArrayEquals(NaiveMatcher.positions(text.toString(), motif.toString()),
                    KmpMatcher.positions(text.toString(), motif.toString()),
                    "trial " + trial + " text=" + text + " motif=" + motif);
        }
    }
}
