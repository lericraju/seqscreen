package com.seqscreen.controller;

import com.seqscreen.dto.BenchmarkRequest;
import com.seqscreen.dto.BenchmarkResponse;
import com.seqscreen.service.BenchmarkService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarks;

    public BenchmarkController(BenchmarkService benchmarks) {
        this.benchmarks = benchmarks;
    }

    @PostMapping
    public BenchmarkResponse run(@RequestBody(required = false) BenchmarkRequest request) {
        return benchmarks.run(request);
    }
}
