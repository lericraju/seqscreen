package com.seqscreen;

import com.seqscreen.algo.BloomFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterTest {

    @Test
    @DisplayName("no false negatives: every inserted key is still possibly present")
    void noFalseNegatives() {
        BloomFilter bloom = new BloomFilter(5_000, 0.01);
        for (long key = 0; key < 5_000; key++) {
            bloom.add(key * 31);
        }
        for (long key = 0; key < 5_000; key++) {
            assertTrue(bloom.mightContain(key * 31), "key " + key * 31 + " was lost");
        }
    }

    @Test
    @DisplayName("false positives stay under the design target")
    void falsePositiveRateWithinTarget() {
        int inserted = 10_000;
        BloomFilter bloom = new BloomFilter(inserted, 0.01);
        for (int key = 0; key < inserted; key++) {
            bloom.add(key);
        }
        int probes = 50_000;
        int falsePositives = 0;
        for (int key = inserted; key < inserted + probes; key++) {
            if (bloom.mightContain(key)) {
                falsePositives++;
            }
        }
        double rate = (double) falsePositives / probes;
        assertTrue(rate <= 0.02, "observed false-positive rate " + rate);
    }

    @Test
    @DisplayName("an empty filter rejects a key")
    void emptyFilter() {
        assertFalse(new BloomFilter(100, 0.01).mightContain(12345L));
    }

    @Test
    @DisplayName("the zero key is a real key, not a sentinel")
    void zeroKey() {
        BloomFilter bloom = new BloomFilter(16, 0.01);
        bloom.add(0L);
        assertTrue(bloom.mightContain(0L));
    }

    @Test
    @DisplayName("sizing grows with the number of keys")
    void sizingGrows() {
        assertTrue(BloomFilter.optimalBits(10_000, 0.01) > BloomFilter.optimalBits(1_000, 0.01));
    }

    @Test
    @DisplayName("a stricter target needs more bits per key")
    void stricterTargetNeedsMoreBits() {
        assertTrue(BloomFilter.optimalBits(1_000, 0.001) > BloomFilter.optimalBits(1_000, 0.1));
    }

    @Test
    @DisplayName("byte size is whole words and the fill ratio rises with inserts")
    void sizeAndFill() {
        BloomFilter bloom = new BloomFilter(1_000, 0.01);
        long before = bloom.byteSize();
        assertTrue(before % Long.BYTES == 0);
        double emptyFill = bloom.fillRatio();
        for (int key = 0; key < 1_000; key++) {
            bloom.add(key);
        }
        assertTrue(bloom.fillRatio() > emptyFill);
        assertTrue(bloom.fillRatio() < 1.0);
    }

    @Test
    @DisplayName("mightContainAll is true only when every key may be present")
    void containAll() {
        BloomFilter bloom = new BloomFilter(100, 0.01);
        SplittableRandom random = new SplittableRandom(3);
        long[] keys = new long[20];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = random.nextLong(1_000_000);
            bloom.add(keys[i]);
        }
        assertTrue(bloom.mightContainAll(keys));
        assertEquals(0, countMissing(bloom, keys));
    }

    private int countMissing(BloomFilter bloom, long[] keys) {
        int missing = 0;
        for (long key : keys) {
            if (!bloom.mightContain(key)) {
                missing++;
            }
        }
        return missing;
    }
}
