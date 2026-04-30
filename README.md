# Soccer Intelligence Engine

Java In-Play Soccer Intelligence Engine is a Spring Boot backend for
coverage-aware Sportradar ingestion, event-sourced match state, probability
snapshots, replay, and backtesting.

The current implementation includes the Stage 1 backend foundation, Stage 2
Sportradar ingestion, and Stage 3 event-sourced state plus feature extraction:
raw payload storage, cache-aware provider calls, coverage detection, normalized
timeline events, deterministic state rebuilds, and model-ready feature snapshots.

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
POST /api/matches/{matchId}/state/rebuild
GET /api/matches/provider?sportEventId=sr:sport_event:70075140
```

Tracking a match now rebuilds state and features from stored events after
ingestion. The track response includes `stateSnapshotsCreated` and
`featureSnapshotsCreated`.

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
SPORTRADAR_REQUEST_DELAY_MS=1500
SPORTRADAR_MAX_RETRIES=2
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
- match tracking/state/events/features REST API

Not included until later stages:

- live polling
- replay endpoints
- probability calculation implementation
- backtesting
