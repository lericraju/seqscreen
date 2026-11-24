package com.seqscreen.index;

import java.util.Arrays;

/**
 * Inverted k-mer index: every distinct k-mer maps to the sequences that contain it.
 *
 * <p>Storage is deliberately primitive. Keys live in an open-addressed long array (linear
 * probing, 2x growth when the load factor passes 0.65) so lookups do no boxing and no object
 * chasing. Each key owns an intrusive singly linked list of sequence numbers held in two flat
 * int arrays, which costs 8 bytes per posting instead of an ArrayList per k-mer.
 *
 * <p>Because a sequence contributes its k-mers one after another, a repeated k-mer inside the
 * same sequence always finds itself at the head of its own list, so duplicates are dropped with
 * a single comparison rather than a set.
 */
public final class KmerIndex {

    private static final double LOAD_FACTOR = 0.65;

    private long[] keys;
    private boolean[] used;
    private int[] head;
    private int[] counts;

    private int[] next;
    private int[] sequenceOf;

    private int slots;
    private int distinct;
    private int occurrences;

    public KmerIndex(int expectedDistinct, int expectedOccurrences) {
        int capacity = tableSize(Math.max(16, (int) (expectedDistinct / LOAD_FACTOR) + 1));
        this.slots = capacity;
        this.keys = new long[capacity];
        this.used = new boolean[capacity];
        this.head = new int[capacity];
        this.counts = new int[capacity];
        Arrays.fill(head, -1);

        int postingCapacity = Math.max(16, expectedOccurrences);
        this.next = new int[postingCapacity];
        this.sequenceOf = new int[postingCapacity];
    }

    /** Links {@code sequence} into the posting list of {@code code}, ignoring a repeat within it. */
    public void add(long code, int sequence) {
        if (!contains(code) && (double) (distinct + 1) / slots > LOAD_FACTOR) {
            grow();
        }
        int slot = slotOf(code);
        if (!used[slot]) {
            used[slot] = true;
            keys[slot] = code;
            head[slot] = -1;
            distinct++;
        }
        int existing = head[slot];
        if (existing >= 0 && sequenceOf[existing] == sequence) {
            return;
        }
        if (occurrences == sequenceOf.length) {
            int grown = Math.max(16, occurrences + (occurrences >>> 1));
            next = Arrays.copyOf(next, grown);
            sequenceOf = Arrays.copyOf(sequenceOf, grown);
        }
        sequenceOf[occurrences] = sequence;
        next[occurrences] = existing;
        head[slot] = occurrences;
        counts[slot]++;
        occurrences++;
    }

    public int[] postings(long code) {
        int slot = slotOf(code);
        if (!used[slot]) {
            return new int[0];
        }
        int[] out = new int[counts[slot]];
        int i = 0;
        for (int p = head[slot]; p >= 0; p = next[p]) {
            out[i++] = sequenceOf[p];
        }
        return out;
    }

    /** Number of sequences containing the k-mer, without materialising the list. */
    public int postingCount(long code) {
        int slot = slotOf(code);
        return used[slot] ? counts[slot] : 0;
    }

    public boolean contains(long code) {
        return used[slotOf(code)];
    }

    public int distinct() {
        return distinct;
    }

    public int occurrences() {
        return occurrences;
    }

    public int capacity() {
        return slots;
    }

    public long memoryBytes() {
        return (long) slots * (Long.BYTES + 1 + Integer.BYTES + Integer.BYTES)
                + (long) occurrences * Integer.BYTES * 2L;
    }

    /**
     * Doubles the table and re-links every key. Posting lists are untouched: only the slot that
     * owns each list head moves, which is why growth costs O(slots) and not O(occurrences).
     */
    private void grow() {
        long[] oldKeys = keys;
        boolean[] oldUsed = used;
        int[] oldHead = head;
        int[] oldCounts = counts;

        int newSlots = slots << 1;
        keys = new long[newSlots];
        used = new boolean[newSlots];
        head = new int[newSlots];
        counts = new int[newSlots];
        Arrays.fill(head, -1);
        slots = newSlots;

        for (int i = 0; i < oldUsed.length; i++) {
            if (!oldUsed[i]) {
                continue;
            }
            int slot = slotOf(oldKeys[i]);
            used[slot] = true;
            keys[slot] = oldKeys[i];
            head[slot] = oldHead[i];
            counts[slot] = oldCounts[i];
        }
    }

    private int slotOf(long code) {
        int mask = slots - 1;
        int slot = (int) mix(code) & mask;
        while (used[slot] && keys[slot] != code) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static int tableSize(int wanted) {
        int size = Integer.highestOneBit(Math.max(16, wanted - 1)) << 1;
        return size;
    }
}
