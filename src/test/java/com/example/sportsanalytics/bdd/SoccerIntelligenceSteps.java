package com.example.sportsanalytics.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sportsanalytics.application.alert.AlertGenerationService;
import com.example.sportsanalytics.application.alert.dto.MatchAlertView;
import com.example.sportsanalytics.application.match.MatchTrackingUseCase;
import com.example.sportsanalytics.application.match.dto.FeatureSnapshotView;
import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.ProbabilityTimelinePoint;
import com.example.sportsanalytics.application.match.dto.ReplayMatchResult;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.AlertType;
import com.example.sportsanalytics.domain.model.CoverageMode;
import com.example.sportsanalytics.domain.model.FeatureSnapshot;
import com.example.sportsanalytics.domain.model.MatchEventType;
import com.example.sportsanalytics.domain.model.MatchState;
import com.example.sportsanalytics.domain.model.Probability;
import com.example.sportsanalytics.domain.model.ProbabilitySnapshot;
import com.example.sportsanalytics.domain.probability.ProbabilityEngine;
import com.example.sportsanalytics.persistence.repository.BacktestRunRepository;
import com.example.sportsanalytics.persistence.repository.FeatureSnapshotRepository;
import com.example.sportsanalytics.persistence.repository.LiveMatchTrackingRepository;
import com.example.sportsanalytics.persistence.repository.MatchAlertRepository;
import com.example.sportsanalytics.persistence.repository.MatchEventRepository;
import com.example.sportsanalytics.persistence.repository.MatchRepository;
import com.example.sportsanalytics.persistence.repository.MatchStateRepository;
import com.example.sportsanalytics.persistence.repository.ProbabilitySnapshotRepository;
import com.example.sportsanalytics.persistence.repository.RawPayloadRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

public class SoccerIntelligenceSteps {
    private static final String RICH_EVENT_ID = "sr:sport_event:demo-rich";
    private static final String BASIC_EVENT_ID = "sr:sport_event:demo-basic";

    @Autowired
    private MatchTrackingUseCase matchTrackingUseCase;
    @Autowired
    private AlertGenerationService alertGenerationService;
    @Autowired
    private ProbabilityEngine probabilityEngine;
    @Autowired
    private MatchAlertRepository matchAlertRepository;
    @Autowired
    private LiveMatchTrackingRepository liveMatchTrackingRepository;
    @Autowired
    private ProbabilitySnapshotRepository probabilitySnapshotRepository;
    @Autowired
    private FeatureSnapshotRepository featureSnapshotRepository;
    @Autowired
    private MatchStateRepository matchStateRepository;
    @Autowired
    private MatchEventRepository matchEventRepository;
    @Autowired
    private RawPayloadRepository rawPayloadRepository;
    @Autowired
    private BacktestRunRepository backtestRunRepository;
    @Autowired
    private MatchRepository matchRepository;

    private String providerMatchId;
    private TrackMatchResult tracked;
    private ReplayMatchResult replay;
    private List<ProbabilityTimelinePoint> timeline;
    private List<MatchAlertView> alerts;
    private double redCardHomeWinBefore;
    private double redCardHomeWinAfter;
    private double earlyGoalSwing;
    private double lateGoalSwing;

    @Before
    public void cleanDatabase() {
        matchAlertRepository.deleteAll();
        liveMatchTrackingRepository.deleteAll();
        probabilitySnapshotRepository.deleteAll();
        featureSnapshotRepository.deleteAll();
        matchStateRepository.deleteAll();
        matchEventRepository.deleteAll();
        backtestRunRepository.deleteAll();
        rawPayloadRepository.deleteAll();
        matchRepository.deleteAll();
        providerMatchId = null;
        tracked = null;
        replay = null;
        timeline = List.of();
        alerts = List.of();
        redCardHomeWinBefore = 0.0;
        redCardHomeWinAfter = 0.0;
        earlyGoalSwing = 0.0;
        lateGoalSwing = 0.0;
    }

    @Given("a rich Sportradar match fixture")
    public void richSportradarMatchFixture() {
        providerMatchId = RICH_EVENT_ID;
    }

    @Given("a basic Sportradar match fixture")
    public void basicSportradarMatchFixture() {
        providerMatchId = BASIC_EVENT_ID;
    }

    @Given("a stored match before a red card")
    public void storedMatchBeforeRedCard() {
        providerMatchId = RICH_EVENT_ID;
        trackCurrentFixture();
    }

    @Given("two otherwise similar match states")
    public void twoOtherwiseSimilarMatchStates() {
        providerMatchId = null;
    }

    @Given("a tracked historical fixture match")
    public void trackedHistoricalFixtureMatch() {
        providerMatchId = RICH_EVENT_ID;
        trackCurrentFixture();
    }

    @Given("provider probability differs from the model by at least the configured threshold")
    public void providerProbabilityDiffersFromModel() {
        providerMatchId = RICH_EVENT_ID;
        trackCurrentFixture();
    }

    @When("the match is tracked")
    public void theMatchIsTracked() {
        trackCurrentFixture();
    }

    @When("a red-card event is replayed")
    public void redCardEventIsReplayed() {
        replay = matchTrackingUseCase.replay(tracked.matchId(), false);
        List<MatchEventView> events = matchTrackingUseCase.events(tracked.matchId(), null);
        timeline = matchTrackingUseCase.probabilityTimeline(tracked.matchId());
        MatchEventView redCard = events.stream()
                .filter(event -> event.eventType().isRedCardEvent())
                .findFirst()
                .orElseThrow();
        ProbabilityTimelinePoint after = timeline.stream()
                .filter(point -> redCard.id().equals(point.eventId()))
                .findFirst()
                .orElseThrow();
        ProbabilityTimelinePoint before = timeline.stream()
                .filter(point -> point.eventSequence() != null && point.eventSequence() < after.eventSequence())
                .max(Comparator.comparing(ProbabilityTimelinePoint::eventSequence))
                .orElseThrow();
        redCardHomeWinBefore = before.homeWin();
        redCardHomeWinAfter = after.homeWin();
    }

    @When("one goal occurs at minute {int} and another at minute {int}")
    public void oneGoalOccursAtMinuteAndAnotherAtMinute(int earlyMinute, int lateMinute) {
        earlyGoalSwing = goalSwingAtMinute(earlyMinute);
        lateGoalSwing = goalSwingAtMinute(lateMinute);
    }

    @When("replay is requested")
    public void replayIsRequested() {
        replay = matchTrackingUseCase.replay(tracked.matchId(), false);
        timeline = matchTrackingUseCase.probabilityTimeline(tracked.matchId());
    }

    @When("alerts are generated")
    public void alertsAreGenerated() {
        alertGenerationService.generate(tracked.matchId());
        alertGenerationService.generate(tracked.matchId());
        alerts = alertGenerationService.alerts(tracked.matchId());
    }

    @Then("coverage is RICH")
    public void coverageIsRich() {
        assertThat(tracked.coverageMode()).isEqualTo(CoverageMode.RICH);
    }

    @Then("coverage is BASIC")
    public void coverageIsBasic() {
        assertThat(tracked.coverageMode()).isEqualTo(CoverageMode.BASIC);
    }

    @Then("feature snapshots include xG and coordinates")
    public void featureSnapshotsIncludeXgAndCoordinates() {
        List<FeatureSnapshotView> features = matchTrackingUseCase.features(tracked.matchId());
        assertThat(features).anySatisfy(feature ->
                assertThat(isFiniteNumber(feature.features().get("xgDelta"))).isTrue()
        );
        assertThat(features).anySatisfy(feature ->
                assertThat(isFiniteNumber(feature.features().get("fieldTilt"))
                        || isFiniteNumber(feature.features().get("shotLocationQualityDelta"))
                        || isFiniteNumber(feature.features().get("possessionPressureDelta"))).isTrue()
        );
    }

    @Then("latest probability confidence is HIGH or MEDIUM")
    public void latestProbabilityConfidenceIsHighOrMedium() {
        ProbabilitySnapshotView probability = matchTrackingUseCase.latestProbability(tracked.matchId());
        assertThat(probability.coverageQuality()).isIn("HIGH", "MEDIUM");
    }

    @Then("latest probabilities sum to {double}")
    public void latestProbabilitiesSumTo(double expected) {
        ProbabilitySnapshotView probability = matchTrackingUseCase.latestProbability(tracked.matchId());
        assertThat(probability.homeWin() + probability.draw() + probability.awayWin()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Then("no probability is outside {int} and {int}")
    public void noProbabilityIsOutsideAnd(int min, int max) {
        ProbabilitySnapshotView probability = matchTrackingUseCase.latestProbability(tracked.matchId());
        assertThat(probability.homeWin()).isBetween((double) min, (double) max);
        assertThat(probability.draw()).isBetween((double) min, (double) max);
        assertThat(probability.awayWin()).isBetween((double) min, (double) max);
    }

    @Then("the advantaged team probability increases")
    public void advantagedTeamProbabilityIncreases() {
        assertThat(replay).isNotNull();
        assertThat(redCardHomeWinAfter).isGreaterThan(redCardHomeWinBefore);
    }

    @Then("the minute {int} goal creates the larger probability movement")
    public void minuteGoalCreatesTheLargerProbabilityMovement(int minute) {
        assertThat(minute).isEqualTo(80);
        assertThat(lateGoalSwing).isGreaterThan(earlyGoalSwing);
    }

    @Then("probability timeline points are ordered by event sequence and minute")
    public void probabilityTimelinePointsAreOrderedByEventSequenceAndMinute() {
        assertThat(replay.probabilitySnapshotsCreated()).isEqualTo(timeline.size());
        assertThat(timeline).isSortedAccordingTo(Comparator
                .comparing((ProbabilityTimelinePoint point) -> point.eventSequence() == null ? Long.MAX_VALUE : point.eventSequence())
                .thenComparingInt(ProbabilityTimelinePoint::minute));
    }

    @Then("a MODEL_PROVIDER_DIVERGENCE alert is persisted once")
    public void modelProviderDivergenceAlertIsPersistedOnce() {
        assertThat(alerts.stream()
                .filter(alert -> alert.alertType() == AlertType.MODEL_PROVIDER_DIVERGENCE)
                .count()).isEqualTo(1);
    }

    private void trackCurrentFixture() {
        assertThat(providerMatchId).isNotBlank();
        tracked = matchTrackingUseCase.track(new TrackMatchCommand(providerMatchId, true));
    }

    private boolean isFiniteNumber(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue());
    }

    private double goalSwingAtMinute(int minute) {
        Probability before = probability(minute, 0, 0);
        Probability after = probability(minute, 1, 0);
        return maxDifference(before, after);
    }

    private Probability probability(int minute, int homeScore, int awayScore) {
        return probabilityEngine.calculate(state(minute, homeScore, awayScore), features(minute, homeScore - awayScore))
                .probability();
    }

    private MatchState state(int minute, int homeScore, int awayScore) {
        return new MatchState(
                "bdd-match",
                "home",
                "away",
                CoverageMode.RICH,
                minute,
                homeScore,
                awayScore,
                0,
                0,
                Map.of(),
                null,
                Map.of(),
                Instant.parse("2026-04-30T00:00:00Z")
        );
    }

    private FeatureSnapshot features(int minute, int scoreDifference) {
        int remaining = Math.max(0, 90 - minute);
        return new FeatureSnapshot(
                "bdd-match",
                minute,
                scoreDifference,
                remaining,
                remaining / 90.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                null,
                CoverageMode.RICH,
                List.of("scoreDifference", "timeRemainingRatio", "homeAdvantage"),
                List.of("providerProbability"),
                Instant.parse("2026-04-30T00:00:00Z")
        );
    }

    private double maxDifference(Probability first, Probability second) {
        return Math.max(
                Math.abs(first.homeWin() - second.homeWin()),
                Math.max(Math.abs(first.draw() - second.draw()), Math.abs(first.awayWin() - second.awayWin()))
        );
    }
}
