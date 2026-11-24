package com.seqscreen.dto;

/** Optional everywhere: omitted fields fall back to the documented defaults. */
public record GenerateRequest(Integer count, Integer length, Long seed, Double plantedFraction,
                              String plantedMotif, Integer k, Double p) {
}
