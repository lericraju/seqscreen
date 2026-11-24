package com.seqscreen.service;

/** Everything a query needs besides the motif and the dataset. */
public record SearchOptions(int maxEdits, boolean bothStrands, int threads) {

    public static final int CONTEXT = 12;

    public static SearchOptions exact() {
        return new SearchOptions(0, true, Threads.defaultCount());
    }

    public static int defaultThreads() {
        return Threads.defaultCount();
    }

    /** One reported occurrence, already resolved back to forward coordinates. */
    public record Hit(int sequenceIndex, String sequenceId, int position, int length,
                      String strand, int edits, String context, int contextStart) {
    }
}
