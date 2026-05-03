Feature: Soccer intelligence acceptance coverage

  Scenario: Rich coverage uses xG and coordinates
    Given a rich Sportradar match fixture
    When the match is tracked
    Then coverage is RICH
    And feature snapshots include xG and coordinates
    And latest probability confidence is HIGH or MEDIUM

  Scenario: Basic coverage still produces valid probabilities
    Given a basic Sportradar match fixture
    When the match is tracked
    Then coverage is BASIC
    And latest probabilities sum to 1.0
    And no probability is outside 0 and 1

  Scenario: Red card changes win probability
    Given a stored match before a red card
    When a red-card event is replayed
    Then the advantaged team probability increases

  Scenario: Late goal creates a larger swing than early goal
    Given two otherwise similar match states
    When one goal occurs at minute 15 and another at minute 80
    Then the minute 80 goal creates the larger probability movement

  Scenario: Replay produces ordered probability timeline
    Given a tracked historical fixture match
    When replay is requested
    Then probability timeline points are ordered by event sequence and minute

  Scenario: Model-provider divergence creates alert
    Given provider probability differs from the model by at least the configured threshold
    When alerts are generated
    Then a MODEL_PROVIDER_DIVERGENCE alert is persisted once
