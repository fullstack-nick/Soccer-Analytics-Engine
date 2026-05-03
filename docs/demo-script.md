# Demo Script

Use this script to show the project in an interview. Keep the demo quota-aware:
prefer one selected historical match, and enable live polling only when you
intentionally want to show it.

## 1. Start The App

```powershell
cd C:\D_DRIVE\Nikita\JS\sports-analytics-engine\soccer-intelligence-engine
docker compose up -d postgres
.\mvnw.cmd test
$env:SPORTRADAR_API_KEY = "your-key"
$env:SPORTRADAR_REQUEST_DELAY_MS = "1100"
.\mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8080/swagger-ui/index.html
```

## 2. Show System Health

Swagger endpoint:

```text
GET /api/system/status
```

Talking points:

- Spring Boot service with PostgreSQL/Flyway.
- The app can start without a Sportradar key.
- The key is required only for provider calls.

## 3. Track One Match

Swagger endpoint:

```text
POST /api/matches/track
```

Body:

```json
{
  "sportEventId": "sr:sport_event:70075140",
  "forceRefresh": true
}
```

Copy the returned `matchId`.

Talking points:

- Provider payloads are stored in `raw_payloads`.
- Provider IDs are never used directly as internal primary keys.
- Coverage detection chooses `RICH`, `STANDARD`, or `BASIC`.
- Events are normalized into the internal domain model.
- The rebuild pipeline creates states, features, probabilities, and alerts.

## 4. Inspect State And Events

Swagger endpoints:

```text
GET /api/matches/{matchId}/state
GET /api/matches/{matchId}/events
GET /api/matches/{matchId}/states
```

Talking points:

- State is rebuilt deterministically from stored events.
- Score-changing goals are separated from goal-like provider events such as
  `possible_goal`.
- Rich event data can include xG, coordinates, outcomes, and player IDs.

## 5. Explain Feature Extraction

Swagger endpoints:

```text
GET /api/matches/{matchId}/features
GET /api/matches/{matchId}/features/latest
```

Talking points:

- Feature snapshots are generated after each event.
- Features use only information available at or before that event.
- Missing rich data is recorded as missing feature metadata instead of throwing.
- This prevents future leakage for replay and backtesting.

## 6. Explain The Probability Engine

Swagger endpoints:

```text
GET /api/matches/{matchId}/probabilities/latest
GET /api/matches/{matchId}/probabilities/timeline
```

Talking points:

- The probability engine is pure Java and independent from Spring.
- It estimates remaining expected goals and uses a Poisson final-score simulation.
- Output includes probabilities, confidence, explanations, and feature contributions.
- Provider probability is comparison context only.

## 7. Replay

Swagger endpoint:

```text
POST /api/matches/{matchId}/replay
```

Body:

```json
{
  "forceRefresh": false
}
```

Talking points:

- Replay reuses the same stored-event pipeline.
- There is no second analytics path for demos or tests.
- This makes debugging and backtesting reproducible.

## 8. Backtest

Swagger endpoint:

```text
POST /api/seasons/{seasonId}/backtests
```

Body for a quota-aware selected-match backtest:

```json
{
  "sportEventIds": ["sr:sport_event:70075140"],
  "forceRefresh": false,
  "continueOnMatchFailure": true
}
```

Talking points:

- Backtests are synchronous and persisted.
- Headline metrics use fixed-minute samples, not final snapshots.
- Metrics compare the model against random, score-only, and provider baselines
  when provider probabilities are available.
- Calibration and event-movement metrics make the model easier to critique.

## 9. Model Comparison And Alerts

Swagger endpoints:

```text
GET /api/matches/{matchId}/model-comparison
GET /api/matches/{matchId}/alerts
```

Talking points:

- Provider probability is not blended into the model.
- Divergence alerts are useful for analytical review.
- Alert deduplication prevents repeated rebuilds from creating duplicates.

## 10. Optional Live Tracking

Only do this when quota allows it.

```powershell
$env:SPORTS_LIVE_ENABLED = "true"
$env:SPORTS_LIVE_RICH_REFRESH_ENABLED = "true"
$env:SPORTS_LIVE_RICH_REFRESH_MS = "120000"
```

Restart the app, then use Swagger:

```text
POST /api/matches/{matchId}/track
GET /api/matches/live
GET /api/matches/{matchId}/alerts
DELETE /api/matches/{matchId}/track
```

Talking points:

- Live tracking is explicitly registered per internal match.
- Scheduled polling is disabled by default.
- Live timeline deltas flow into the same `match_events` table and rebuild
  pipeline used by historical matches.
- For `RICH` matches, the optional rich refresh keeps xG and coordinates fresh
  by periodically fetching `sport_events/{id}/extended_timeline`.

## Model Limits To State Clearly

- The model is a credible simplified analytics model, not a commercial odds model.
- Accuracy depends on match coverage and provider data availability.
- Current tuning is conservative and is evaluated against baselines.
- The project demonstrates backend engineering and model-evaluation thinking.
