create table live_match_tracking (
    id uuid primary key,
    match_id uuid not null references matches(id),
    provider_match_id varchar(80) not null,
    tracking_status varchar(40) not null,
    active boolean not null,
    started_at timestamp with time zone not null,
    stopped_at timestamp with time zone,
    last_poll_at timestamp with time zone,
    last_success_at timestamp with time zone,
    last_error_at timestamp with time zone,
    error_count integer not null default 0,
    last_error varchar(1000),
    last_delta_payload_id uuid references raw_payloads(id),
    last_full_timeline_payload_id uuid references raw_payloads(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create unique index uk_live_match_tracking_match
    on live_match_tracking(match_id);

create index ix_live_match_tracking_active_status
    on live_match_tracking(active, tracking_status);

create table match_alerts (
    id uuid primary key,
    match_id uuid not null references matches(id),
    event_id uuid references match_events(id),
    probability_snapshot_id uuid references probability_snapshots(id),
    alert_type varchar(60) not null,
    severity varchar(20) not null,
    minute integer not null,
    title varchar(160) not null,
    message varchar(1000) not null,
    details_json jsonb not null default '{}'::jsonb,
    deduplication_key varchar(180) not null,
    created_at timestamp with time zone not null
);

create unique index uk_match_alerts_match_deduplication
    on match_alerts(match_id, deduplication_key);

create index ix_match_alerts_match
    on match_alerts(match_id);

create index ix_match_alerts_alert_type
    on match_alerts(alert_type);

create index ix_match_alerts_created_at
    on match_alerts(created_at);
