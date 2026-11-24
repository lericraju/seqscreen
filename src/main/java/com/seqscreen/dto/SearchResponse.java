package com.seqscreen.dto;

import java.util.List;

public record SearchResponse(String motif, String reverseMotif, List<MatchDto> matches,
                             SearchStats stats) {
}
