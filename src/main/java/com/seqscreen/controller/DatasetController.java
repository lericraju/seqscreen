package com.seqscreen.controller;

import com.seqscreen.dto.DatasetInfo;
import com.seqscreen.dto.GenerateRequest;
import com.seqscreen.service.DatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}
