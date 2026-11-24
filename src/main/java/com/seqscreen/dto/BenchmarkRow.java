package com.seqscreen.dto;

public record BenchmarkRow(int sequences, long totalBases, double buildMs, double indexBuildMs,
                           double naiveMs, double kmpMs, double bloomKmpMs, double indexKmpMs,
                           double speedupVsKmp, long bloomBytes, long indexBytes, long textBytes,
                           int confirmed, int indexCandidates, int falsePositives,
                           boolean resultsIdentical, boolean indexSkipped, String note) {
}
