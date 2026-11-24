package com.seqscreen.dto;

/**
 * Everything the reader needs to judge a result: how much of the dataset was skipped, how much
 * of the surviving candidate set was real, and which screening applied.
 */
public record SearchStats(String mode, int motifLength, int totalSequences, boolean screened,
                          int screenedOut, int candidates, int bloomPasses, int confirmedSequences,
                          int falsePositives, double observedFalsePositiveRate,
                          String screeningNote, boolean bothStrands, int threads,
                          double elapsedMs, int matches, boolean truncated, int matchLimit) {
}
