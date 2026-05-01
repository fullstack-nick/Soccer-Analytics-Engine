# Soccer Intelligence Engine

Java In-Play Soccer Intelligence Engine is a Spring Boot backend for
coverage-aware Sportradar ingestion, event-sourced match state, probability
snapshots, replay, and backtesting.

The current implementation includes the Stage 1 backend foundation, Stage 2
Sportradar ingestion, Stage 3 event-sourced state plus feature extraction,
Stage 4 explainable probability generation, Stage 5 replay/backtesting,
Stage 5.5 model-evaluation hardening, and Stage 6 live tracking/alerts:
raw payload storage, cache-aware provider calls, coverage detection, normalized
timeline events, deterministic state rebuilds, model-ready feature snapshots,
persisted win/draw/loss probability timelines, synchronous backtest runs, and
model-vs-provider comparison views, explicit live tracking, scheduled live
polling, and persisted match intelligence alerts.

## Stack

- Java 21
- Spring Boot 4.0.6
- Maven Wrapper
- PostgreSQL 16
- Flyway
- Spring Data JPA
- Swagger/OpenAPI via SpringDoc
- Docker Compose

## Run Locally

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

Useful URLs:

- Health: <http://localhost:8080/actuator/health>
- System status: <http://localhost:8080/api/system/status>
- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## Sportradar Match Tracking

Set your API key before calling Sportradar:

```powershell
$env:SPORTRADAR_API_KEY = "your-key"
```

Track one sport event:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/matches/track `
  -ContentType "application/json" `
  -Body '{"sportEventId":"sr:sport_event:70075140","forceRefresh":true}'
```

Read stored data:

```text
GET /api/matches/{matchId}/state
GET /api/matches/{matchId}/states
GET /api/matches/{matchId}/events
GET /api/matches/{matchId}/events?type=GOAL
GET /api/matches/{matchId}/features
GET /api/matches/{matchId}/features/latest
GET /api/matches/{matchId}/probabilities
GET /api/matches/{matchId}/probabilities/latest
GET /api/matches/{matchId}/probabilities/timeline
POST /api/matches/{matchId}/replay
GET /api/matches/{matchId}/model-comparison
POST /api/matches/{matchId}/track
DELETE /api/matches/{matchId}/track
GET /api/matches/live
GET /api/matches/{matchId}/alerts
POST /api/matches/{matchId}/state/rebuild
POST /api/matches/{matchId}/probabilities/rebuild
GET /api/matches/provider?sportEventId=sr:sport_event:70075140
```

Tracking a match now rebuilds state and features from stored events after
ingestion, then regenerates probability snapshots. The track response includes
`stateSnapshotsCreated`, `featureSnapshotsCreated`, and
`probabilitySnapshotsCreated`.

## Stage 3 Features

The feature pipeline creates one `feature_snapshots` row per event, plus one
summary-only snapshot when no timeline is available. Feature values only use
events and momentum values available at or before the snapshot minute, so the
pipeline is ready for replay/backtesting without future leakage.

Feature snapshots include score difference, time remaining, home advantage,
standings/form team strength, lineup adjustment, red-card adjustment, rolling
xG delta, shot pressure, shot location quality, field tilt, possession pressure,
momentum trend, provider probability when available, and available/missing
feature metadata.

## Stage 4 Probability Engine

The probability engine is a pure Java `ProbabilityEngine` implementation named
`ExpectedGoalsProbabilityEngine`. It uses the current event-sourced score as the
fixed starting point, estimates remaining expected goals from coverage-aware
features, then runs a Poisson-style final-score simulation to produce:

- home win probability
- draw probability
- away win probability
- model confidence
- coverage quality: `HIGH`, `MEDIUM`, or `LOW`
- explanations
- signed feature contributions

Provider probability, when available from Sportradar season probabilities, is
used only as comparison context. It is not blended into this model's output.

Probability snapshots are generated automatically after `POST /api/matches/track`
and `POST /api/matches/{matchId}/state/rebuild`. They can also be regenerated
explicitly with `POST /api/matches/{matchId}/probabilities/rebuild`.

## Stage 5 Replay, Backtesting, And Evaluation

Replay reuses the same stored-event rebuild pipeline as tracking:

```text
POST /api/matches/{matchId}/replay
```

With `forceRefresh=false`, replay regenerates state, feature, and probability
snapshots from already stored events. With `forceRefresh=true`, it re-fetches
the provider match first, then rebuilds the same analytics timeline.

Backtests run synchronously and persist the completed run:

```text
POST /api/seasons/{seasonId}/backtests
GET /api/backtests/{runId}
```

Request body:

```json
{
  "sportEventIds": [],
  "forceRefresh": false,
  "continueOnMatchFailure": true
}
```

When `sportEventIds` is empty, the service fetches the Sportradar season
schedule and processes finished matches. When IDs are provided, only those
matches are processed under the given season ID. A backtest stores status,
requested/processed/failed counts, per-match failures, and versioned metrics.

The Stage 5.5 metrics JSON uses `evaluationVersion=stage5.5-v1`. Its headline
score is based on fixed-minute samples only: `0`, `15`, `30`, `HT`, `60`, `75`,
and `85`. Final snapshots are reported separately as diagnostics because they
mostly measure whether the model can read the final score.

Backtest metrics also include all in-play snapshots, random/score-only/provider
baselines, minute-bucket calibration, per-match summaries, and average
probability movement by event type. Provider probability remains comparison
context only.

Event semantics now separate more real Sportradar event categories:
`SET_PIECE`, `OFFSIDE`, `PENALTY`, and `INJURY`. Set pieces and non-scoring
penalty/offside events can contribute to pressure features, while confirmed
score-changing events remain the only events treated as goals.

Model comparison is available per match:

```text
GET /api/matches/{matchId}/model-comparison
```

Provider probability is read from feature snapshots when Sportradar season
probabilities are available. It is comparison context only; the probability
engine does not blend provider values into its own output.

## Stage 6 Live Tracking And Alerts

Live tracking is explicit and disabled by default to protect Sportradar quota.
To track a live match, first store it with `POST /api/matches/track`, then start
live tracking by internal match ID:

```text
POST /api/matches/{matchId}/track
DELETE /api/matches/{matchId}/track
GET /api/matches/live
GET /api/matches/{matchId}/alerts
```

When enabled, the scheduled poller reads Sportradar live schedules, live
timeline delta, and occasional full live timelines. New or updated live events
are written into the same `match_events` table used by historical tracking, then
the existing rebuild pipeline regenerates state, features, probabilities, and
alerts.

Alert rules currently cover provider/model divergence, red-card probability
swings, pressure despite losing, xG contradicting the scoreline, and late
momentum shifts. Alerts are deduplicated by deterministic keys so repeated
rebuilds or repeated live deltas do not create duplicate alert rows.

## Configuration

Runtime defaults are safe for local development:

```yaml
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/soccer_intelligence
SPRING_DATASOURCE_USERNAME=soccer
SPRING_DATASOURCE_PASSWORD=soccer
SPORTRADAR_API_KEY=
SPORTRADAR_LOCALE=en
SPORTRADAR_ACCESS_LEVEL=trial
SPORTRADAR_PACKAGE_NAME=soccer-extended
SPORTRADAR_BASE_URL=https://api.sportradar.com
SPORTRADAR_REQUEST_DELAY_MS=1100
SPORTRADAR_MAX_RETRIES=2
SPORTS_LIVE_ENABLED=false
SPORTS_LIVE_POLL_DELAY_MS=10000
SPORTS_LIVE_FULL_TIMELINE_REFRESH_MS=60000
SPORTS_LIVE_MAX_MATCHES_PER_TICK=3
```

## Verify

```powershell
.\mvnw.cmd test
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

## Current Scope

Included:

- application shell and package structure
- configuration binding
- core domain contracts
- PostgreSQL/Flyway schema
- JPA entities and repositories
- system status REST endpoint
- Swagger/OpenAPI
- Docker and GitLab CI
- Sportradar REST client using `RestClient`
- raw payload cache/storage with sanitized request paths
- coverage detection: `RICH`, `STANDARD`, `BASIC`
- normalized timeline event persistence
- optional standings, form standings, and season probabilities
- event-sourced state rebuilds from stored events
- persisted state timeline
- persisted feature timeline
- expected-goals/Poisson probability engine
- persisted probability timeline with explanations and feature contributions
- historical match replay over stored events
- synchronous season or selected-match backtests
- fixed-minute Brier score, log loss, calibration, baselines, diagnostics, and event-movement metrics
- hardened event semantics for set pieces, offside, penalties, and injuries
- model-vs-provider probability divergence comparison
- explicit live match tracking registry
- scheduled live schedules/timeline-delta polling, disabled by default
- live events ingested into the same event store and rebuild pipeline
- persisted alert generation with deduplication
- match tracking/state/events/features REST API
- probability REST API
- live tracking and alert REST API

Not included until later stages:

- Kafka/RabbitMQ streaming
- frontend dashboard
