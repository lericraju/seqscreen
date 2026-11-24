package com.seqscreen.index;

/** One named sequence, as read from FASTA or generated. Bases are upper case A/C/G/T/N. */
public record SequenceRecord(String id, String bases) {

    public SequenceRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("sequence id is required");
        }
        if (bases == null) {
            throw new IllegalArgumentException("sequence bases are required");
        }
    }
}
