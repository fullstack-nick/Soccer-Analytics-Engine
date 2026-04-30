package com.example.sportsanalytics.api.system;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sportsanalytics.application.system.SystemStatusProvider;
import com.example.sportsanalytics.application.system.SystemStatusResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

@WebMvcTest(SystemStatusController.class)
@Import(SystemStatusControllerTest.TestStatusConfiguration.class)
class SystemStatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSystemStatus() throws Exception {
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("soccer-intelligence-engine"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.activeProfiles", contains("test")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @TestConfiguration
    static class TestStatusConfiguration {
        @Bean
        SystemStatusProvider systemStatusProvider() {
            return () -> new SystemStatusResponse(
                    "soccer-intelligence-engine",
                    "0.1.0-SNAPSHOT",
                    "UP",
                    "UP",
                    List.of("test"),
                    Instant.parse("2026-04-30T00:00:00Z")
            );
        }
    }
}
