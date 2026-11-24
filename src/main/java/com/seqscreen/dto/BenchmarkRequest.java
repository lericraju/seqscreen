package com.seqscreen.dto;

import java.util.List;

public record BenchmarkRequest(List<Integer> sizes, Integer length, String motif,
                               Double plantedFraction, Integer k, Double p) {
}
