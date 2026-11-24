package com.seqscreen.dto;

public record SearchRequest(String motif, String mode, Integer mismatches, Boolean bothStrands,
                            Integer threads) {
}
