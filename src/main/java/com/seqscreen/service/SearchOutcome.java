package com.seqscreen.service;

import com.seqscreen.service.SearchOptions.Hit;

import java.util.List;

/**
 * What a search actually did, kept separate from the response DTO so the benchmark can reuse it
 * without going through JSON.
 */
public record SearchOutcome(List<Hit> hits, int candidates, int screenedOut, int confirmedSequences,
                            int falsePositives, boolean screened, String screeningNote,
                            boolean indexUsed, int threads, double elapsedMs, int sequenceCount) {
}
