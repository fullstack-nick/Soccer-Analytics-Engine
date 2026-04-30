create table raw_payloads (
    id uuid primary key,
    source_endpoint varchar(160) not null,
    provider_id varchar(80),
    fetched_at timestamp with time zone not null,
    payload_json jsonb not null
);

create table matches (
    id uuid primary key,
    provider_match_id varchar(80) not null unique,
    season_id varchar(80) not null,
    competition_id varchar(80) not null,
    home_team_id varchar(80) not null,
    away_team_id varchar(80) not null,
    start_time timestamp with time zone,
    coverage_mode varchar(20) not null
);

create table match_events (
    id uuid primary key,
    match_id uuid not null references matches(id),
    provider_event_id varchar(80),
    event_sequence bigint not null,
    event_type varchar(40) not null,
    occurred_at_minute integer not null,
    stoppage_time integer,
    team_side varchar(20) not null,
    payload_id uuid references raw_payloads(id),
    received_at timestamp with time zone not null,
    constraint uk_match_events_match_provider_event unique (match_id, provider_event_id)
);

create table match_states (
    id uuid primary key,
    match_id uuid not null references matches(id),
    version bigint not null,
    minute integer not null,
    home_score integer not null,
    away_score integer not null,
    home_red_cards integer not null,
    away_red_cards integer not null,
    state_json jsonb not null,
    updated_at timestamp with time zone not null,
    constraint uk_match_states_match_version unique (match_id, version)
);

create table feature_snapshots (
    id uuid primary key,
    match_id uuid not null references matches(id),
    event_id uuid references match_events(id),
    minute integer not null,
    features_json jsonb not null,
    created_at timestamp with time zone not null
);

create table probability_snapshots (
    id uuid primary key,
    match_id uuid not null references matches(id),
    event_id uuid references match_events(id),
    minute integer not null,
    home_win double precision not null,
    draw double precision not null,
    away_win double precision not null,
    explanations_json jsonb not null,
    created_at timestamp with time zone not null
);

create table backtest_runs (
    id uuid primary key,
    season_id varchar(80) not null,
    model_version varchar(80) not null,
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone,
    metrics_json jsonb
);

create index ix_raw_payloads_provider_id on raw_payloads(provider_id);
create index ix_raw_payloads_source_endpoint on raw_payloads(source_endpoint);
create index ix_raw_payloads_fetched_at on raw_payloads(fetched_at);

create index ix_matches_season_id on matches(season_id);
create index ix_matches_competition_id on matches(competition_id);
create index ix_matches_coverage_mode on matches(coverage_mode);

create index ix_match_events_match_sequence on match_events(match_id, event_sequence);
create index ix_match_events_provider_event_id on match_events(provider_event_id);
create index ix_match_events_received_at on match_events(received_at);

create index ix_match_states_match_updated_at on match_states(match_id, updated_at);
create index ix_feature_snapshots_match_minute on feature_snapshots(match_id, minute);
create index ix_probability_snapshots_match_created_at on probability_snapshots(match_id, created_at);
create index ix_backtest_runs_season_id on backtest_runs(season_id);
