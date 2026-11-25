package com.seqscreen.controller;

import com.seqscreen.dto.DatasetInfo;
import com.seqscreen.dto.GenerateRequest;
import com.seqscreen.dto.SampleInfo;
import com.seqscreen.service.DatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    private final DatasetService datasets;

    public DatasetController(DatasetService datasets) {
        this.datasets = datasets;
    }

    @GetMapping
    public DatasetInfo info() {
        return datasets.info();
    }

    @PostMapping("/generate")
    public DatasetInfo generate(@RequestBody(required = false) GenerateRequest request) {
        return datasets.generate(request);
    }

    @PostMapping("/upload")
    public DatasetInfo upload(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "k", required = false) Integer k,
                              @RequestParam(value = "p", required = false) Double p) {
        return datasets.upload(file, k, p);
    }

    /** The FASTA files bundled in the jar, so the page can offer them before loading one. */
    @GetMapping("/samples")
    public List<SampleInfo> samples() {
        return datasets.samples();
    }

    @PostMapping("/sample/{name}")
    public DatasetInfo loadSample(@PathVariable("name") String name,
                                  @RequestParam(value = "k", required = false) Integer k,
                                  @RequestParam(value = "p", required = false) Double p) {
        return datasets.loadSample(name, k, p);
    }
}
