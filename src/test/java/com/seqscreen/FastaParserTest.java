package com.seqscreen;

import com.seqscreen.index.SequenceRecord;
import com.seqscreen.validation.ApiException;
import com.seqscreen.validation.FastaParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastaParserTest {

    @Test
    @DisplayName("a simple two-record file parses with ids and bases")
    void parsesTwoRecords() {
        FastaParser.Result result = FastaParser.parse(">alpha\nACGT\n>beta\nTTTT\n");
        assertEquals(2, result.records().size());
        assertEquals("alpha", result.records().get(0).id());
        assertEquals("ACGT", result.records().get(0).bases());
        assertEquals("TTTT", result.records().get(1).bases());
    }

    @Test
    @DisplayName("lower case and blank lines are normalised away")
    void normalisesLowercaseAndBlankLines() {
        FastaParser.Result result = FastaParser.parse(">a\n\nacgt\n\nACGT\n\n");
        assertEquals(1, result.records().size());
        assertEquals("ACGTACGT", result.records().get(0).bases());
    }

    @Test
    @DisplayName("an unknown base rejects the whole file")
    void rejectsUnknownBase() {
        Executable call = () -> FastaParser.parse(">a\nACGXT\n");
        ApiException error = assertThrows(ApiException.class, call);
        assertEquals(400, error.status());
        assertTrue(error.getMessage().contains("'X'"), error.getMessage());
    }

    @Test
    @DisplayName("a repeated id keeps the first record and counts the rest as skipped")
    void skipsDuplicates() {
        FastaParser.Result result = FastaParser.parse(">a\nACGT\n>a\nTTTT\n>b\nGGGG\n");
        assertEquals(2, result.records().size());
        assertEquals(1, result.skippedDuplicates());
        assertEquals("ACGT", result.records().get(0).bases());
    }

    @Test
    @DisplayName("a header with no bases is skipped and counted")
    void skipsEmptyRecord() {
        FastaParser.Result result = FastaParser.parse(">empty\n>full\nACGT\n");
        assertEquals(1, result.records().size());
        assertEquals(1, result.skippedEmpty());
    }

    @Test
    @DisplayName("a file with no sequences is rejected")
    void rejectsFileWithoutRecords() {
        Executable call = () -> FastaParser.parse(">empty\n>alsoEmpty\n");
        assertThrows(ApiException.class, call);
    }

    @Test
    @DisplayName("sequence data before any header is rejected")
    void rejectsLeadingData() {
        Executable call = () -> FastaParser.parse("ACGT\n>a\nACGT\n");
        assertThrows(ApiException.class, call);
    }

    @Test
    @DisplayName("an empty file is rejected with a helpful message")
    void rejectsEmptyFile() {
        Executable call = () -> FastaParser.parse("   \n");
        ApiException error = assertThrows(ApiException.class, call);
        assertTrue(error.getMessage().contains("empty"), error.getMessage());
    }

    @Test
    @DisplayName("N bases are accepted")
    void acceptsN() {
        List<SequenceRecord> records = FastaParser.parse(">n\nACGTNNNN\n").records();
        assertEquals("ACGTNNNN", records.get(0).bases());
    }
}
