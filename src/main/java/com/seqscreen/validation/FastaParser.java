package com.seqscreen.validation;

import com.seqscreen.index.SequenceRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FASTA reader with the validation the app depends on: only A/C/G/T/N are accepted, blank lines
 * and stray whitespace are ignored, records without bases are skipped, and a repeated identifier
 * keeps its first occurrence so a motif can be reported against one unambiguous sequence.
 *
 * <p>Anything that would silently corrupt a result is an error instead: an unknown base, a file
 * with no records, or a record whose bases never appear.
 */
public final class FastaParser {

    public record Result(List<SequenceRecord> records, int skippedDuplicates, int skippedEmpty) {
    }

    private FastaParser() {
    }

    public static Result parse(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("The file is empty. A FASTA file starts with a '>' header line.");
        }
        List<SequenceRecord> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skippedDuplicates = 0;
        int skippedEmpty = 0;

        String header = null;
        StringBuilder bases = new StringBuilder();
        int lineNumber = 0;
        boolean anyHeader = false;

        for (String rawLine : text.split("\r?\n", -1)) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(">")) {
                anyHeader = true;
                if (header != null) {
                    if (!flush(header, bases, records, seen)) {
                        if (bases.length() == 0) {
                            skippedEmpty++;
                        } else {
                            skippedDuplicates++;
                        }
                    }
                }
                header = line.substring(1).strip();
                if (header.isEmpty()) {
                    header = "sequence-" + (records.size() + 1);
                }
                bases.setLength(0);
                continue;
            }
            if (header == null) {
                throw ApiException.badRequest("Line " + lineNumber
                        + " has sequence data before any '>' header line.");
            }
            for (int i = 0; i < line.length(); i++) {
                char c = Character.toUpperCase(line.charAt(i));
                if (c == 'A' || c == 'C' || c == 'G' || c == 'T' || c == 'N') {
                    bases.append(c);
                } else {
                    throw ApiException.badRequest("Line " + lineNumber + " contains '" + line.charAt(i)
                            + "', which is not a DNA base. Allowed: A, C, G, T, N.");
                }
            }
        }

        if (!anyHeader) {
            throw ApiException.badRequest("No '>' header line found. Is this a FASTA file?");
        }
        if (header != null) {
            if (!flush(header, bases, records, seen)) {
                if (bases.length() == 0) {
                    skippedEmpty++;
                } else {
                    skippedDuplicates++;
                }
            }
        }
        if (records.isEmpty()) {
            throw ApiException.badRequest("The file contains no sequences with bases.");
        }
        return new Result(records, skippedDuplicates, skippedEmpty);
    }

    /** Adds the record when it has bases and a fresh identifier; returns false when skipped. */
    private static boolean flush(String header, StringBuilder bases, List<SequenceRecord> records,
                                 Set<String> seen) {
        if (bases.length() == 0) {
            return false;
        }
        if (!seen.add(header)) {
            return false;
        }
        records.add(new SequenceRecord(header, bases.toString()));
        return true;
    }
}
