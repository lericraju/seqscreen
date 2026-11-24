package com.seqscreen.dto;

public record DatasetInfo(int sequences, long totalBases, int k, double p, boolean indexBuilt,
                          long indexBytes, long bloomBytes, int distinctKmers, String source,
                          String indexNote, String bloomNote) {
}
