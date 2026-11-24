package com.seqscreen.controller;

import com.seqscreen.algo.RevComp;
import com.seqscreen.dto.MatchDto;
import com.seqscreen.dto.SearchRequest;
import com.seqscreen.dto.SearchResponse;
import com.seqscreen.dto.SearchStats;
import com.seqscreen.index.SequenceIndex;
import com.seqscreen.service.DatasetService;
import com.seqscreen.service.SearchMode;
import com.seqscreen.service.SearchOptions;
import com.seqscreen.service.SearchOutcome;
import com.seqscreen.service.SearchService;
import com.seqscreen.validation.MotifValidator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final DatasetService datasets;
    private final SearchService search;

    public SearchController(DatasetService datasets, SearchService search) {
        this.datasets = datasets;
        this.search = search;
    }

    @PostMapping
    public SearchResponse search(@RequestBody SearchRequest request) {
        if (request == null) {
            throw new com.seqscreen.validation.ApiException(400, "A search request is required.");
        }
        int edits = request.mismatches() == null ? 0 : request.mismatches();
        MotifValidator.validateEdits(edits);
        String motif = MotifValidator.validate(request.motif(), edits);

        int threads = request.threads() == null ? SearchOptions.defaultThreads() : request.threads();
        MotifValidator.validateThreads(threads);
        boolean bothStrands = request.bothStrands() == null || request.bothStrands();
        SearchMode mode = SearchMode.parse(request.mode());

        SequenceIndex index = datasets.require();
        SearchOutcome outcome = search.run(index, motif, mode, new SearchOptions(edits, bothStrands, threads));

        List<MatchDto> matches = new ArrayList<>(outcome.hits().size());
        outcome.hits().forEach(hit -> matches.add(new MatchDto(hit.sequenceId(), hit.position(),
                hit.strand(), hit.edits(), hit.context(), hit.contextStart())));

        double rate = outcome.candidates() == 0 ? 0
                : (double) outcome.falsePositives() / outcome.candidates();
        // A sequence that passes the Bloom screen is only "possibly present" until KMP confirms it.
        int bloomPasses = mode == SearchMode.BLOOM_KMP && outcome.screened() ? outcome.candidates() : 0;
        SearchStats stats = new SearchStats(mode.name(), motif.length(), index.size(),
                outcome.screened(), outcome.screenedOut(), outcome.candidates(), bloomPasses,
                outcome.confirmedSequences(), outcome.falsePositives(), rate,
                outcome.screeningNote(), bothStrands, outcome.threads(),
                Math.round(outcome.elapsedMs() * 10.0) / 10.0, matches.size(),
                matches.size() >= SearchService.MATCH_LIMIT, SearchService.MATCH_LIMIT);

        return new SearchResponse(motif, bothStrands ? RevComp.of(motif) : null, matches, stats);
    }
}
