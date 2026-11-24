package com.seqscreen.service;

/**
 * How many workers a query may use when the caller does not say. Capped at 8: past that the
 * dataset of a single query is split thin enough that scheduling costs more than the work moved.
 */
public final class Threads {

    public static final int MAX = 8;

    private Threads() {
    }

    public static int defaultCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(cores, MAX));
    }

    /** Never start more workers than there are sequences to check. */
    public static int forWork(int requested, int workItems) {
        int wanted = Math.min(Math.max(1, requested), MAX);
        return Math.max(1, Math.min(wanted, workItems));
    }
}
