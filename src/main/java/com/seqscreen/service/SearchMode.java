package com.seqscreen.service;

import com.seqscreen.validation.ApiException;

/** The four strategies the benchmark compares, in increasing amount of precomputation. */
public enum SearchMode {
    /** Brute force over every sequence. The baseline. */
    NAIVE,
    /** Linear-time exact matching over every sequence. */
    KMP,
    /** Bloom filter per sequence, then KMP to confirm. */
    BLOOM_KMP,
    /** Inverted k-mer index, then KMP to confirm. */
    INDEX_KMP;

    public static SearchMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return INDEX_KMP;
        }
        String normalised = raw.strip().toUpperCase().replace('-', '_');
        return switch (normalised) {
            case "NAIVE", "BRUTE", "BRUTE_FORCE" -> NAIVE;
            case "KMP" -> KMP;
            case "BLOOM", "BLOOM_KMP", "BLOOMKMP" -> BLOOM_KMP;
            case "INDEX", "INDEX_KMP", "INDEXKMP", "INVERTED" -> INDEX_KMP;
            default -> throw ApiException.badRequest("Unknown mode '" + raw
                    + "'. Use NAIVE, KMP, BLOOM_KMP or INDEX_KMP.");
        };
    }

    /** True when this mode screens candidates before confirming them exactly. */
    public boolean screens() {
        return this == BLOOM_KMP || this == INDEX_KMP;
    }
}
