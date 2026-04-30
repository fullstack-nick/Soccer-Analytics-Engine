# Soccer Intelligence Engine

Java In-Play Soccer Intelligence Engine is a Spring Boot backend for
coverage-aware Sportradar ingestion, event-sourced match state, probability
snapshots, replay, and backtesting.

The current implementation includes the Stage 1 backend foundation plus Stage 2
Sportradar ingestion: raw payload storage, cache-aware provider calls, coverage
detection, normalized timeline events, and projected match state.

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
GET /api/matches/{matchId}/events
GET /api/matches/{matchId}/events?type=GOAL
GET /api/matches/provider?sportEventId=sr:sport_event:70075140
```

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
- projected latest match state
- match tracking/state/events REST API

Not included until later stages:

- live polling
- replay endpoints
- probability calculation implementation
- feature snapshots
- backtesting
