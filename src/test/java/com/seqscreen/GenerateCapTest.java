package com.seqscreen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cap is read from {@code MAX_GENERATE_BASES}; a small hosted instance sets it low so a single
 * request cannot ask for more sequence than the box can hold.
 */
@SpringBootTest(properties = "MAX_GENERATE_BASES=5000")
@AutoConfigureMockMvc
class GenerateCapTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("generation over MAX_GENERATE_BASES is refused with a 400 and a clear message")
    void overCapIsRefused() throws Exception {
        mvc.perform(post("/api/dataset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1000,\"length\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("base limit")));
    }

    @Test
    @DisplayName("generation inside the cap still builds the dataset")
    void insideCapBuilds() throws Exception {
        mvc.perform(post("/api/dataset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":4,\"length\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequences").value(4))
                .andExpect(jsonPath("$.totalBases").value(800));
    }
}
