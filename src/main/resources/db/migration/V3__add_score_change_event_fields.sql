alter table match_events
    add column provider_event_type varchar(80),
    add column home_score_after integer,
    add column away_score_after integer,
    add column score_changed boolean not null default false;

create index ix_match_events_provider_event_type
    on match_events(provider_event_type);

create index ix_match_events_match_score_changed
    on match_events(match_id, score_changed);
