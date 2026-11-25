package com.seqscreen.dto;

/** A FASTA file bundled in the jar, described so the page can offer it before it is loaded. */
public record SampleInfo(String name, String label, int sequences, long bases) {
}
