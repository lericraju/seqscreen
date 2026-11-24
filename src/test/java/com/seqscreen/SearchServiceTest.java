package com.seqscreen;

import com.seqscreen.index.SequenceIndex;
import com.seqscreen.index.SequenceRecord;
import com.seqscreen.service.DatasetService;
import com.seqscreen.service.SearchMode;
import com.seqscreen.service.SearchOptions;
import com.seqscreen.service.SearchOutcome;
import com.seqscreen.service.SearchOptions.Hit;
import com.seqscreen.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {

    private static final String MOTIF = "GATTACAGATTACA";
    private static final int K = 8;

    private final SearchService service = new SearchService();

    private static SearchOptions exact() {
        return new SearchOptions(0, true, 1);
    }

    private static SequenceIndex index(List<SequenceRecord> records) {
        return SequenceIndex.build(records, K, 0.01);
    }

    private static String signature(SearchOutcome outcome) {
        StringBuilder builder = new StringBuilder();
        for (Hit hit : outcome.hits()) {
            builder.append(hit.sequenceIndex()).append(':').append(hit.position())
                    .append(':').append(hit.strand()).append(';');
        }
        return builder.toString();
    }

    @Test
    @DisplayName("all four modes find the planted motif in every sequence")
    void everyModeFindsPlantedMotif() {
        SequenceIndex idx = index(DatasetService.synthesize(200, 300, 42, 1.0, MOTIF));
        for (SearchMode mode : SearchMode.values()) {
            SearchOutcome outcome = service.run(idx, MOTIF, mode, exact());
            assertTrue(outcome.confirmedSequences() >= 200,
                    mode + " confirmed only " + outcome.confirmedSequences());
        }
    }

    @Test
    @DisplayName("a hit that exists only on the reverse strand is flagged as reverse")
    void reverseOnlyHitIsFlagged() {
        // revcomp(GATTACA) = TGTAATC, present here without the forward motif.
        SequenceIndex idx = index(List.of(new SequenceRecord("rev", "TTTTTTGTAATCCCCCC")));
        SearchOutcome outcome = service.run(idx, "GATTACA", SearchMode.INDEX_KMP, exact());
        assertEquals(1, outcome.hits().size(), signature(outcome));
        assertEquals("reverse", outcome.hits().get(0).strand());
        assertEquals(5, outcome.hits().get(0).position());
    }

    @Test
    @DisplayName("both strands report their own hits")
    void bothStrandsReported() {
        SequenceIndex idx = index(List.of(
                new SequenceRecord("forward", "AAAAGATTACATTTT"),
                new SequenceRecord("reverse", "AAAATGTAATCTTTT")));
        SearchOutcome outcome = service.run(idx, "GATTACA", SearchMode.INDEX_KMP, exact());
        assertEquals(2, outcome.hits().size(), signature(outcome));
        assertEquals("forward", outcome.hits().get(0).strand());
        assertEquals("reverse", outcome.hits().get(1).strand());
    }

    @Test
    @DisplayName("a palindromic motif is reported once per position")
    void palindromeReportedOnce() {
        SequenceIndex idx = index(List.of(new SequenceRecord("p", "ACGTACGT")));
        SearchOutcome outcome = service.run(idx, "ACGT", SearchMode.INDEX_KMP, exact());
        assertEquals(2, outcome.hits().size(), signature(outcome));
        assertTrue(outcome.hits().stream().allMatch(hit -> hit.strand().equals("forward")));
    }

    @Test
    @DisplayName("the index cuts the candidate set far below the dataset size")
    void indexReducesCandidates() {
        SequenceIndex idx = index(DatasetService.synthesize(2_000, 300, 21, 0.0, MOTIF));
        SearchOutcome outcome = service.run(idx, MOTIF, SearchMode.INDEX_KMP, exact());
        assertTrue(outcome.screened());
        assertTrue(outcome.indexUsed());
        assertTrue(outcome.hits().isEmpty());
        assertTrue(outcome.candidates() < 2_000, "index kept " + outcome.candidates());
        assertTrue(outcome.screenedOut() > 0);
    }

    @Test
    @DisplayName("Bloom filters also cut the candidate set")
    void bloomReducesCandidates() {
        SequenceIndex idx = index(DatasetService.synthesize(500, 300, 22, 0.0, MOTIF));
        SearchOutcome outcome = service.run(idx, MOTIF, SearchMode.BLOOM_KMP, exact());
        assertTrue(outcome.screened());
        assertTrue(outcome.candidates() < 500, "bloom kept " + outcome.candidates());
    }

    @Test
    @DisplayName("approximate search cannot be screened and says so")
    void approximateScansEverything() {
        SequenceIndex idx = index(DatasetService.synthesize(100, 200, 23, 0.0, MOTIF));
        SearchOutcome outcome = service.run(idx, MOTIF, SearchMode.INDEX_KMP, new SearchOptions(1, true, 1));
        assertFalse(outcome.screened());
        assertEquals(100, outcome.candidates());
        assertTrue(outcome.screeningNote().contains("Approximate"), outcome.screeningNote());
    }

    @Test
    @DisplayName("a wildcard motif cannot be screened either")
    void wildcardScansEverything() {
        SequenceIndex idx = index(DatasetService.synthesize(100, 200, 24, 0.0, MOTIF));
        SearchOutcome outcome = service.run(idx, "GATTNCA", SearchMode.INDEX_KMP, exact());
        assertFalse(outcome.screened());
        assertTrue(outcome.screeningNote().contains("Wildcard"), outcome.screeningNote());
    }

    @Test
    @DisplayName("a motif shorter than k skips screening")
    void shorterThanKSkipsScreening() {
        SequenceIndex idx = index(DatasetService.synthesize(50, 200, 25, 0.0, MOTIF));
        SearchOutcome outcome = service.run(idx, "GAT", SearchMode.INDEX_KMP, exact());
        assertFalse(outcome.screened());
        assertTrue(outcome.screeningNote().contains("shorter than k"), outcome.screeningNote());
    }

    @Test
    @DisplayName("k above the packed-key limit skips the index but keeps Bloom screening")
    void indexSkippedForLargeK() {
        String longMotif = "GATTACAGATTACAGATTACAGATTACAGAT"; // 30 bases
        SequenceIndex idx = SequenceIndex.build(
                DatasetService.synthesize(50, 200, 26, 1.0, longMotif), 20, 0.01);
        assertFalse(idx.indexBuilt());
        assertTrue(idx.indexNote().contains("exceeds"), idx.indexNote());
        SearchOutcome outcome = service.run(idx, longMotif, SearchMode.INDEX_KMP, exact());
        assertTrue(outcome.confirmedSequences() >= 50);
        assertFalse(outcome.indexUsed());
    }

    @Test
    @DisplayName("thread count never changes the result")
    void threadsProduceIdenticalResults() {
        SequenceIndex idx = index(DatasetService.synthesize(400, 300, 27, 0.05, MOTIF));
        SearchOutcome single = service.run(idx, MOTIF, SearchMode.INDEX_KMP, new SearchOptions(0, true, 1));
        SearchOutcome many = service.run(idx, MOTIF, SearchMode.INDEX_KMP, new SearchOptions(0, true, 6));
        assertEquals(signature(single), signature(many));
        assertTrue(many.threads() > 1);
    }

    @Test
    @DisplayName("an absent motif produces no hits in any mode")
    void noHitsForAbsentMotif() {
        SequenceIndex idx = index(DatasetService.synthesize(100, 200, 28, 0.0, MOTIF));
        for (SearchMode mode : SearchMode.values()) {
            assertEquals(0, service.run(idx, MOTIF, mode, exact()).hits().size(), mode.name());
        }
    }

    @Test
    @DisplayName("every overlapping occurrence is reported")
    void overlappingOccurrencesReported() {
        SequenceIndex idx = index(List.of(new SequenceRecord("a", "AAAAAAA")));
        SearchOutcome outcome = service.run(idx, "AAA", SearchMode.INDEX_KMP, exact());
        assertEquals(5, outcome.hits().size(), signature(outcome));
    }

    @Test
    @DisplayName("an empty dataset searches to nothing without failing")
    void emptyDataset() {
        SequenceIndex idx = index(List.of());
        SearchOutcome outcome = service.run(idx, MOTIF, SearchMode.INDEX_KMP, exact());
        assertEquals(0, outcome.hits().size());
        assertEquals(0, outcome.candidates());
    }

    @Test
    @DisplayName("the reported context contains the hit and its offset is consistent")
    void hitContextSurroundsTheMatch() {
        SequenceIndex idx = index(List.of(new SequenceRecord("c", "CCCCCCCCCCGATTACAGGGGGGGGGG")));
        SearchOutcome outcome = service.run(idx, "GATTACA", SearchMode.INDEX_KMP, exact());
        Hit hit = outcome.hits().get(0);
        assertEquals(10, hit.position());
        assertEquals(0, hit.contextStart());
        assertTrue(hit.context().contains("GATTACA"), hit.context());
    }

    @Test
    @DisplayName("a very common motif is capped at the match limit")
    void matchLimitCapsTheList() {
        SequenceIndex idx = SequenceIndex.build(
                List.of(new SequenceRecord("a", "A".repeat(3000))), 1, 0.01);
        SearchOutcome outcome = service.run(idx, "A", SearchMode.INDEX_KMP, exact());
        assertEquals(SearchService.MATCH_LIMIT, outcome.hits().size());
    }

    @Test
    @DisplayName("all four modes agree on the exact hit list")
    void allModesAgreeOnHits() {
        SequenceIndex idx = index(DatasetService.synthesize(300, 300, 29, 0.05, MOTIF));
        String reference = signature(service.run(idx, MOTIF, SearchMode.NAIVE, exact()));
        for (SearchMode mode : SearchMode.values()) {
            assertEquals(reference, signature(service.run(idx, MOTIF, mode, exact())), mode.name());
        }
    }

    @Test
    @DisplayName("every search explains how it screened")
    void screeningNoteIsAlwaysPresent() {
        SequenceIndex idx = index(DatasetService.synthesize(100, 200, 30, 0.01, MOTIF));
        for (SearchMode mode : SearchMode.values()) {
            String note = service.run(idx, MOTIF, mode, exact()).screeningNote();
            assertTrue(note != null && !note.isBlank(), mode.name());
        }
    }
}
