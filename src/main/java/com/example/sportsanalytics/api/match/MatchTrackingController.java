package com.example.sportsanalytics.api.match;

import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.alert.dto.MatchAlertView;
import com.example.sportsanalytics.application.live.LiveTrackingService;
import com.example.sportsanalytics.application.live.dto.LiveTrackingView;
import com.example.sportsanalytics.application.match.MatchTrackingUseCase;
import com.example.sportsanalytics.application.match.dto.FeatureSnapshotView;
import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.ProbabilityTimelinePoint;
import com.example.sportsanalytics.application.match.dto.RebuildMatchStateResult;
import com.example.sportsanalytics.application.match.dto.RebuildProbabilityResult;
import com.example.sportsanalytics.application.match.dto.ReplayMatchResult;
import com.example.sportsanalytics.application.match.dto.StoredMatchView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.analytics.comparison.ModelComparisonResult;
import com.example.sportsanalytics.domain.model.MatchEventType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Matches")
@RestController
@RequestMapping("/api/matches")
public class MatchTrackingController {
    private final MatchTrackingUseCase matchTrackingUseCase;
    private final LiveTrackingService liveTrackingService;
    private final AlertGenerationService alertGenerationService;

    public MatchTrackingController(
            MatchTrackingUseCase matchTrackingUseCase,
            LiveTrackingService liveTrackingService,
            AlertGenerationService alertGenerationService
    ) {
        this.matchTrackingUseCase = matchTrackingUseCase;
        this.liveTrackingService = liveTrackingService;
        this.alertGenerationService = alertGenerationService;
    }

    @Operation(summary = "Track one Sportradar sport event and persist normalized match state")
    @PostMapping("/track")
    public TrackMatchResult track(@Valid @RequestBody TrackMatchRequest request) {
        return matchTrackingUseCase.track(new TrackMatchCommand(request.sportEventId(), request.forceRefresh()));
    }

    @Operation(summary = "Start live tracking for an already stored match")
    @PostMapping("/{matchId}/track")
    public LiveTrackingView startLiveTracking(@PathVariable UUID matchId) {
        return liveTrackingService.start(matchId);
    }

    @Operation(summary = "Stop live tracking for a match without deleting stored analytics")
    @DeleteMapping("/{matchId}/track")
    public LiveTrackingView stopLiveTracking(@PathVariable UUID matchId) {
        return liveTrackingService.stop(matchId);
    }

    @Operation(summary = "List live-tracked matches with latest analytics summary")
    @GetMapping("/live")
    public List<LiveTrackingView> liveMatches() {
        return liveTrackingService.trackedMatches();
    }

    @Operation(summary = "Resolve a stored match by Sportradar sport event id")
    @GetMapping("/provider")
    public StoredMatchView byProviderId(@RequestParam String sportEventId) {
        return matchTrackingUseCase.findByProviderMatchId(sportEventId);
    }

    @Operation(summary = "Return latest projected state for a stored match")
    @GetMapping("/{matchId}/state")
    public MatchStateView state(@PathVariable UUID matchId) {
        return matchTrackingUseCase.latestState(matchId);
    }

    @Operation(summary = "Rebuild persisted state and feature snapshots from stored events")
    @PostMapping("/{matchId}/state/rebuild")
    public RebuildMatchStateResult rebuildState(@PathVariable UUID matchId) {
        return matchTrackingUseCase.rebuildState(matchId);
    }

    @Operation(summary = "Replay a stored match through the event-sourced analytics pipeline")
    @PostMapping("/{matchId}/replay")
    public ReplayMatchResult replay(
            @PathVariable UUID matchId,
            @RequestBody(required = false) ReplayMatchRequest request
    ) {
        return matchTrackingUseCase.replay(matchId, request != null && request.forceRefresh());
    }

    @Operation(summary = "Rebuild persisted probability snapshots from stored state and feature snapshots")
    @PostMapping("/{matchId}/probabilities/rebuild")
    public RebuildProbabilityResult rebuildProbabilities(@PathVariable UUID matchId) {
        return matchTrackingUseCase.rebuildProbabilities(matchId);
    }

    @Operation(summary = "Return persisted state timeline for a stored match")
    @GetMapping("/{matchId}/states")
    public List<MatchStateView> states(@PathVariable UUID matchId) {
        return matchTrackingUseCase.states(matchId);
    }

    @Operation(summary = "Return normalized events for a stored match")
    @GetMapping("/{matchId}/events")
    public List<MatchEventView> events(
            @PathVariable UUID matchId,
            @RequestParam(required = false) MatchEventType type
    ) {
        return matchTrackingUseCase.events(matchId, type);
    }

    @Operation(summary = "Return feature snapshot timeline for a stored match")
    @GetMapping("/{matchId}/features")
    public List<FeatureSnapshotView> features(@PathVariable UUID matchId) {
        return matchTrackingUseCase.features(matchId);
    }

    @Operation(summary = "Return latest feature snapshot for a stored match")
    @GetMapping("/{matchId}/features/latest")
    public FeatureSnapshotView latestFeature(@PathVariable UUID matchId) {
        return matchTrackingUseCase.latestFeature(matchId);
    }

    @Operation(summary = "Return probability snapshot timeline for a stored match")
    @GetMapping("/{matchId}/probabilities")
    public List<ProbabilitySnapshotView> probabilities(@PathVariable UUID matchId) {
        return matchTrackingUseCase.probabilities(matchId);
    }

    @Operation(summary = "Return probability timeline points with score context")
    @GetMapping("/{matchId}/probabilities/timeline")
    public List<ProbabilityTimelinePoint> probabilityTimeline(@PathVariable UUID matchId) {
        return matchTrackingUseCase.probabilityTimeline(matchId);
    }

    @Operation(summary = "Return latest probability snapshot for a stored match")
    @GetMapping("/{matchId}/probabilities/latest")
    public ProbabilitySnapshotView latestProbability(@PathVariable UUID matchId) {
        return matchTrackingUseCase.latestProbability(matchId);
    }

    @Operation(summary = "Compare model probability against provider probability when available")
    @GetMapping("/{matchId}/model-comparison")
    public ModelComparisonResult modelComparison(@PathVariable UUID matchId) {
        return matchTrackingUseCase.modelComparison(matchId);
    }

    @Operation(summary = "Return analytical alerts for a stored match")
    @GetMapping("/{matchId}/alerts")
    public List<MatchAlertView> alerts(@PathVariable UUID matchId) {
        return alertGenerationService.alerts(matchId);
    }
}
