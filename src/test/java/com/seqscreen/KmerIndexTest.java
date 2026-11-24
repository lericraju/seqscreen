package com.seqscreen;

import com.seqscreen.algo.KmerEncoder;
import com.seqscreen.index.KmerIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KmerIndexTest {

    private static long code(String kmer) {
        return KmerEncoder.encode(kmer, 0, kmer.length());
    }

    @Test
    @DisplayName("a k-mer maps to every sequence containing it")
    void findsSequences() {
        KmerIndex index = new KmerIndex(16, 16);
        index.add(code("ACGTA"), 0);
        index.add(code("ACGTA"), 3);
        index.add(code("TTTTT"), 1);
        int[] postings = index.postings(code("ACGTA"));
        Arrays.sort(postings);
        assertArrayEqualsInts(new int[]{0, 3}, postings);
    }

    @Test
    @DisplayName("a repeated k-mer inside one sequence is stored once")
    void dedupesWithinSequence() {
        KmerIndex index = new KmerIndex(16, 16);
        index.add(code("AAAA"), 2);
        index.add(code("AAAA"), 2);
        index.add(code("AAAA"), 2);
        assertEquals(1, index.postings(code("AAAA")).length);
        assertEquals(1, index.postingCount(code("AAAA")));
    }

    @Test
    @DisplayName("a k-mer no sequence contains has no postings")
    void absentKmer() {
        KmerIndex index = new KmerIndex(16, 16);
        index.add(code("ACGT"), 0);
        assertFalse(index.contains(code("TGCA")));
        assertEquals(0, index.postings(code("TGCA")).length);
    }

    @Test
    @DisplayName("the all-A k-mer is a valid key, not a sentinel")
    void zeroKey() {
        KmerIndex index = new KmerIndex(8, 8);
        assertEquals(0L, code("AAAA"));
        index.add(code("AAAA"), 5);
        assertTrue(index.contains(code("AAAA")));
        assertEquals(5, index.postings(code("AAAA"))[0]);
    }

    @Test
    @DisplayName("distinct counts unique k-mers only")
    void distinctCount() {
        KmerIndex index = new KmerIndex(16, 16);
        index.add(code("ACGT"), 0);
        index.add(code("ACGT"), 1);
        index.add(code("ACGT"), 2);
        index.add(code("ACGA"), 0);
        assertEquals(2, index.distinct());
        assertEquals(4, index.occurrences());
    }

    @Test
    @DisplayName("the table keeps working after it grows past its initial capacity")
    void growsPastCapacity() {
        KmerIndex index = new KmerIndex(4, 8);
        int inserted = 500;
        for (int i = 0; i < inserted; i++) {
            index.add(code("ACGT") + i, i);
        }
        assertEquals(inserted, index.distinct());
        for (int i = 0; i < inserted; i++) {
            assertEquals(i, index.postings(code("ACGT") + i)[0]);
        }
        assertTrue(index.capacity() > 4);
    }

    @Test
    @DisplayName("memory estimate covers the table and the postings")
    void memoryEstimate() {
        KmerIndex index = new KmerIndex(1_000, 4_000);
        index.add(code("ACGT"), 0);
        long bytes = index.memoryBytes();
        assertTrue(bytes > 4_000L * Integer.BYTES * 2, "estimate " + bytes);
    }

    private static void assertArrayEqualsInts(int[] expected, int[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
