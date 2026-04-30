package com.example.sportsanalytics.api.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSmokeTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/system/status']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/track']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/state']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/state/rebuild']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/states']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/events']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/features']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/features/latest']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/probabilities/rebuild']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/probabilities']").exists())
                .andExpect(jsonPath("$.paths['/api/matches/{matchId}/probabilities/latest']").exists());
    }
}
