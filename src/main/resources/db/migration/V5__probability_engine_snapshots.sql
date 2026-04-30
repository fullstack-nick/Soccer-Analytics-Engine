alter table probability_snapshots
    add column model_version varchar(40) not null default 'xg-poisson-v1',
    add column model_confidence double precision,
    add column coverage_quality varchar(20),
    add column feature_contributions_json jsonb not null default '{}'::jsonb;

create index ix_probability_snapshots_match_event
    on probability_snapshots(match_id, event_id);

create index ix_probability_snapshots_match_created_at_v5
    on probability_snapshots(match_id, created_at);

create index ix_probability_snapshots_match_minute
    on probability_snapshots(match_id, minute);
