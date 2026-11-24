package com.seqscreen.dto;

import java.util.List;

public record BenchmarkResponse(String motif, int k, double p, int sequenceLength,
                                boolean repetitive, List<BenchmarkRow> rows, List<String> notes) {
}
