# API Examples

These examples assume the app is running at `http://localhost:8080`.

```powershell
cd C:\D_DRIVE\Nikita\JS\sports-analytics-engine\soccer-intelligence-engine
```

## Health And Status

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health |
  ConvertTo-Json -Depth 10

Invoke-RestMethod http://localhost:8080/api/system/status |
  ConvertTo-Json -Depth 10
```

## Track One Match

```powershell
$track = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/matches/track `
  -ContentType "application/json" `
  -Body '{"sportEventId":"sr:sport_event:70075140","forceRefresh":true}'

$track | ConvertTo-Json -Depth 10
$matchId = $track.matchId
```

## Resolve Provider ID

```powershell
$providerId = [uri]::EscapeDataString("sr:sport_event:70075140")

Invoke-RestMethod "http://localhost:8080/api/matches/provider?sportEventId=$providerId" |
  ConvertTo-Json -Depth 10
```

## Latest State

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/state" |
  ConvertTo-Json -Depth 10
```

## Event Timeline

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/events" |
  ConvertTo-Json -Depth 10

Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/events?type=GOAL" |
  ConvertTo-Json -Depth 10
```

## Feature Snapshots

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/features/latest" |
  ConvertTo-Json -Depth 10

Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/features" |
  ConvertTo-Json -Depth 10
```

## Latest Probability

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/probabilities/latest" |
  ConvertTo-Json -Depth 10
```

## Probability Timeline

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/probabilities/timeline" |
  ConvertTo-Json -Depth 10
```

## Replay

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/matches/$matchId/replay" `
  -ContentType "application/json" `
  -Body '{"forceRefresh":false}' |
  ConvertTo-Json -Depth 10
```

## Rebuild State And Probabilities

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/matches/$matchId/state/rebuild" |
  ConvertTo-Json -Depth 10

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/matches/$matchId/probabilities/rebuild" |
  ConvertTo-Json -Depth 10
```

## Selected-Match Backtest

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/seasons/sr:season:demo/backtests `
  -ContentType "application/json" `
  -Body '{"sportEventIds":["sr:sport_event:70075140"],"forceRefresh":false,"continueOnMatchFailure":true}' |
  ConvertTo-Json -Depth 20
```

## Get A Backtest Run

```powershell
$runId = "replace-with-backtest-run-id"

Invoke-RestMethod "http://localhost:8080/api/backtests/$runId" |
  ConvertTo-Json -Depth 20
```

## Model Comparison

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/model-comparison" |
  ConvertTo-Json -Depth 20
```

## Alerts

```powershell
Invoke-RestMethod "http://localhost:8080/api/matches/$matchId/alerts" |
  ConvertTo-Json -Depth 20
```

## Live Tracking

Live polling is disabled by default. Enable it only when you intend to spend
Sportradar quota:

```powershell
$env:SPORTS_LIVE_ENABLED = "true"
$env:SPORTRADAR_REQUEST_DELAY_MS = "1100"
```

Restart the app, then:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/matches/$matchId/track" |
  ConvertTo-Json -Depth 10

Invoke-RestMethod http://localhost:8080/api/matches/live |
  ConvertTo-Json -Depth 20

Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/api/matches/$matchId/track" |
  ConvertTo-Json -Depth 10
```

## Swagger

```text
http://localhost:8080/swagger-ui/index.html
```
