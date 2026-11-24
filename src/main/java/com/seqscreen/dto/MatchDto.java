package com.seqscreen.dto;

/**
 * One reported hit, always in forward coordinates. {@code strand} says where the motif was
 * actually found, {@code edits} is the allowed budget (0 for exact), and {@code context} is the
 * surrounding bases starting at {@code contextStart} in the sequence.
 */
public record MatchDto(String sequenceId, int position, String strand, int edits,
                       String context, int contextStart) {
}
