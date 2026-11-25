package com.seqscreen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DatasetApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("the health endpoint answers without touching the dataset")
    void healthIsCheap() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("the bundled samples are listed with their real sizes")
    void samplesAreListed() throws Exception {
        mvc.perform(get("/api/dataset/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'sample.fasta')].sequences", hasItem(30)))
                .andExpect(jsonPath("$[?(@.name == 'sample.fasta')].bases", hasItem(7200)))
                .andExpect(jsonPath("$[?(@.name == 'reverse-strand.fasta')].sequences", hasItem(3)));
    }

    @Test
    @DisplayName("loading a bundled sample installs it and reports its counts")
    void sampleLoads() throws Exception {
        mvc.perform(post("/api/dataset/sample/sample.fasta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequences").value(30))
                .andExpect(jsonPath("$.totalBases").value(7200))
                .andExpect(jsonPath("$.source").value("sample.fasta"));
    }

    @Test
    @DisplayName("an unknown sample name is rejected and the real names are listed")
    void unknownSampleRejected() throws Exception {
        mvc.perform(post("/api/dataset/sample/nope.fasta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("sample.fasta")));
    }
}
