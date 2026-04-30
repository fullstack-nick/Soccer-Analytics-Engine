package com.example.sportsanalytics.analytics.features;

import com.example.sportsanalytics.domain.model.Probability;
import com.example.sportsanalytics.sportradar.mapping.MatchMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProviderFeatureContextExtractor {
    public ProviderFeatureContext extract(
            MatchMetadata metadata,
            JsonNode standings,
            JsonNode formStandings,
            JsonNode seasonProbabilities
    ) {
        Double teamStrengthDelta = teamStrengthDelta(
                standings,
                formStandings,
                metadata.homeTeam().id(),
                metadata.awayTeam().id()
        ).orElse(null);
        Probability providerProbability = providerProbability(
                seasonProbabilities,
                metadata.providerMatchId()
        ).orElse(null);
        return new ProviderFeatureContext(teamStrengthDelta, providerProbability);
    }

    private Optional<Double> teamStrengthDelta(JsonNode standings, JsonNode formStandings, String homeTeamId, String awayTeamId) {
        Optional<Double> homeStanding = teamScore(standings, homeTeamId);
        Optional<Double> awayStanding = teamScore(standings, awayTeamId);
        Optional<Double> homeForm = teamScore(formStandings, homeTeamId);
        Optional<Double> awayForm = teamScore(formStandings, awayTeamId);

        Optional<Double> home = combinedScore(homeStanding, homeForm);
        Optional<Double> away = combinedScore(awayStanding, awayForm);
        if (home.isEmpty() || away.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(home.get() - away.get());
    }

    private Optional<Double> combinedScore(Optional<Double> standing, Optional<Double> form) {
        if (standing.isPresent() && form.isPresent()) {
            return Optional.of(standing.get() + (form.get() * 0.5));
        }
        return standing.or(() -> form);
    }

    private Optional<Double> teamScore(JsonNode root, String teamId) {
        if (root == null || root.isMissingNode() || root.isNull() || teamId == null) {
            return Optional.empty();
        }
        List<Double> scores = new ArrayList<>();
        collectTeamScores(root, teamId, scores);
        return scores.stream().findFirst();
    }

    private void collectTeamScores(JsonNode node, String teamId, List<Double> scores) {
        if (node == null || scores.size() > 8) {
            return;
        }
        if (node.isObject()) {
            if (matchesTeam(node, teamId)) {
                scoreFromNode(node).ifPresent(scores::add);
            }
            node.fields().forEachRemaining(entry -> collectTeamScores(entry.getValue(), teamId, scores));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectTeamScores(child, teamId, scores);
            }
        }
    }

    private boolean matchesTeam(JsonNode node, String teamId) {
        if (teamId.equals(node.path("id").asText(null))) {
            return true;
        }
        if (teamId.equals(node.path("competitor").path("id").asText(null))) {
            return true;
        }
        return teamId.equals(node.path("team").path("id").asText(null));
    }

    private Optional<Double> scoreFromNode(JsonNode node) {
        JsonNode statistics = node.path("statistics").isObject() ? node.path("statistics") : node;
        Optional<Double> points = firstDouble(statistics, "points", "pts");
        Optional<Double> played = firstDouble(statistics, "played", "matches_played", "matches", "played_total");
        if (played.isEmpty()) {
            double wins = firstDouble(statistics, "wins", "win").orElse(0.0);
            double draws = firstDouble(statistics, "draws", "draw").orElse(0.0);
            double losses = firstDouble(statistics, "losses", "loss").orElse(0.0);
            double total = wins + draws + losses;
            if (total > 0.0) {
                played = Optional.of(total);
            }
        }
        if (points.isPresent() && played.isPresent() && played.get() > 0.0) {
            double goalsFor = firstDouble(statistics, "goals_for", "score_for", "goals_scored").orElse(0.0);
            double goalsAgainst = firstDouble(statistics, "goals_against", "score_against", "goals_conceded").orElse(0.0);
            double pointsPerGame = points.get() / played.get();
            double goalDiffPerGame = (goalsFor - goalsAgainst) / played.get();
            return Optional.of(pointsPerGame + (goalDiffPerGame * 0.1));
        }
        String form = node.path("form").asText(null);
        if (form != null && !form.isBlank()) {
            return formScore(form);
        }
        return Optional.empty();
    }

    private Optional<Double> formScore(String form) {
        int matches = 0;
        int points = 0;
        for (char value : form.toUpperCase(Locale.ROOT).toCharArray()) {
            if (value == 'W') {
                points += 3;
                matches++;
            } else if (value == 'D') {
                points += 1;
                matches++;
            } else if (value == 'L') {
                matches++;
            }
        }
        return matches == 0 ? Optional.empty() : Optional.of(points / (double) matches);
    }

    private Optional<Double> firstDouble(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return Optional.of(value.asDouble());
            }
            if (value.isTextual()) {
                try {
                    return Optional.of(Double.parseDouble(value.asText()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Probability> providerProbability(JsonNode root, String providerMatchId) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Optional.empty();
        }
        List<JsonNode> candidates = new ArrayList<>();
        collectProbabilityCandidates(root, providerMatchId, candidates);
        for (JsonNode candidate : candidates) {
            Optional<Probability> probability = probabilityFromCandidate(candidate);
            if (probability.isPresent()) {
                return probability;
            }
        }
        return Optional.empty();
    }

    private void collectProbabilityCandidates(JsonNode node, String providerMatchId, List<JsonNode> candidates) {
        if (node == null || candidates.size() > 8) {
            return;
        }
        if (node.isObject()) {
            if (providerMatchId.equals(node.path("sport_event").path("id").asText(null))
                    || providerMatchId.equals(node.path("sport_event").path("@id").asText(null))) {
                candidates.add(node);
            }
            node.fields().forEachRemaining(entry -> collectProbabilityCandidates(entry.getValue(), providerMatchId, candidates));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectProbabilityCandidates(child, providerMatchId, candidates);
            }
        }
    }

    private Optional<Probability> probabilityFromCandidate(JsonNode candidate) {
        List<JsonNode> markets = jsonArray(candidate.path("markets"));
        if (markets.isEmpty()) {
            markets = jsonArray(candidate.path("market"));
        }
        for (JsonNode market : markets) {
            List<JsonNode> outcomes = jsonArray(market.path("outcomes"));
            if (outcomes.isEmpty()) {
                outcomes = jsonArray(market.path("outcome"));
            }
            if (outcomes.size() < 3) {
                continue;
            }
            Optional<Probability> probability = probabilityFromOutcomes(outcomes);
            if (probability.isPresent()) {
                return probability;
            }
        }
        return Optional.empty();
    }

    private Optional<Probability> probabilityFromOutcomes(List<JsonNode> outcomes) {
        Double home = null;
        Double draw = null;
        Double away = null;
        for (JsonNode outcome : outcomes) {
            String name = outcome.path("name").asText("").toLowerCase(Locale.ROOT);
            double probability = outcome.path("probability").asDouble(Double.NaN);
            if (!Double.isFinite(probability)) {
                continue;
            }
            if (name.contains("draw") || name.equals("x")) {
                draw = probability;
            } else if (name.contains("home") || name.equals("1")) {
                home = probability;
            } else if (name.contains("away") || name.equals("2")) {
                away = probability;
            }
        }
        if ((home == null || draw == null || away == null) && outcomes.size() >= 3) {
            home = outcomes.get(0).path("probability").asDouble(Double.NaN);
            draw = outcomes.get(1).path("probability").asDouble(Double.NaN);
            away = outcomes.get(2).path("probability").asDouble(Double.NaN);
        }
        if (home == null || draw == null || away == null
                || !Double.isFinite(home) || !Double.isFinite(draw) || !Double.isFinite(away)) {
            return Optional.empty();
        }
        if (home > 1.0 || draw > 1.0 || away > 1.0) {
            home /= 100.0;
            draw /= 100.0;
            away /= 100.0;
        }
        double sum = home + draw + away;
        if (sum <= 0.0) {
            return Optional.empty();
        }
        return Optional.of(new Probability(home / sum, draw / sum, away / sum));
    }

    private List<JsonNode> jsonArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(values::add);
            return values;
        }
        return List.of(node);
    }
}
