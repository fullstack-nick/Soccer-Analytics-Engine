package com.example.sportsanalytics.api.system;

import com.example.sportsanalytics.application.system.SystemStatusProvider;
import com.example.sportsanalytics.application.system.SystemStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System")
public class SystemStatusController {
    private final SystemStatusProvider statusProvider;

    public SystemStatusController(SystemStatusProvider statusProvider) {
        this.statusProvider = statusProvider;
    }

    @GetMapping("/status")
    @Operation(summary = "Returns application and database health information")
    public SystemStatusResponse status() {
        return statusProvider.currentStatus();
    }
}
