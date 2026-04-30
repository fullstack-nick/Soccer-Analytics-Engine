alter table match_states
    add column event_id uuid references match_events(id);

alter table feature_snapshots
    add column coverage_mode varchar(20) not null default 'BASIC',
    add column feature_set_version varchar(40) not null default 'stage3-v1',
    add column availability_json jsonb not null default '{"availableFeatures":[],"missingFeatures":[]}'::jsonb;

create index ix_match_states_match_event
    on match_states(match_id, event_id);

create index ix_match_states_match_version
    on match_states(match_id, version);

create index ix_feature_snapshots_match_event
    on feature_snapshots(match_id, event_id);

create index ix_feature_snapshots_coverage_mode
    on feature_snapshots(coverage_mode);
