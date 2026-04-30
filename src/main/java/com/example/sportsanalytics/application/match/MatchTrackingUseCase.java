package com.example.sportsanalytics.application.match;

import com.example.sportsanalytics.application.match.dto.MatchEventView;
import com.example.sportsanalytics.application.match.dto.FeatureSnapshotView;
import com.example.sportsanalytics.application.match.dto.MatchStateView;
import com.example.sportsanalytics.application.match.dto.ProbabilitySnapshotView;
import com.example.sportsanalytics.application.match.dto.RebuildMatchStateResult;
import com.example.sportsanalytics.application.match.dto.RebuildProbabilityResult;
import com.example.sportsanalytics.application.match.dto.StoredMatchView;
import com.example.sportsanalytics.application.match.dto.TrackMatchCommand;
import com.example.sportsanalytics.application.match.dto.TrackMatchResult;
import com.example.sportsanalytics.domain.model.MatchEventType;
import java.util.List;
import java.util.UUID;

public interface MatchTrackingUseCase {
    TrackMatchResult track(TrackMatchCommand command);

    MatchStateView latestState(UUID matchId);

    List<MatchStateView> states(UUID matchId);

    List<MatchEventView> events(UUID matchId, MatchEventType type);

    RebuildMatchStateResult rebuildState(UUID matchId);

    RebuildProbabilityResult rebuildProbabilities(UUID matchId);

    List<FeatureSnapshotView> features(UUID matchId);

    FeatureSnapshotView latestFeature(UUID matchId);

    List<ProbabilitySnapshotView> probabilities(UUID matchId);

    ProbabilitySnapshotView latestProbability(UUID matchId);

    StoredMatchView findByProviderMatchId(String providerMatchId);
}
