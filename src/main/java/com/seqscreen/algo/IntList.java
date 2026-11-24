package com.seqscreen.algo;

import java.util.Arrays;

/** Growable int array. Matchers return positions through this to avoid boxing. */
public final class IntList {

    private int[] data;
    private int size;

    public IntList() {
        this(16);
    }

    public IntList(int capacity) {
        this.data = new int[Math.max(4, capacity)];
    }

    public void add(int value) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = value;
    }

    public int get(int i) {
        return data[i];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int[] toArray() {
        return Arrays.copyOf(data, size);
    }
}
