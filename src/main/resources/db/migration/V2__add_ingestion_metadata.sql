alter table raw_payloads
    add column http_status integer not null default 200,
    add column cache_control varchar(255),
    add column expires_at timestamp with time zone,
    add column provider_package varchar(40) not null default 'soccer-extended',
    add column request_path varchar(255) not null default '';

update raw_payloads
set request_path = source_endpoint
where request_path = '';

alter table match_events
    add column player_ids jsonb not null default '[]'::jsonb,
    add column x integer,
    add column y integer,
    add column destination_x integer,
    add column destination_y integer,
    add column xg_value double precision,
    add column outcome varchar(80),
    add column source_timeline_type varchar(40) not null default 'UNKNOWN';

create index ix_raw_payloads_cache_lookup
    on raw_payloads(provider_id, source_endpoint, request_path, expires_at, fetched_at);

create index ix_match_events_match_type
    on match_events(match_id, event_type);

create index ix_match_events_source_timeline_type
    on match_events(source_timeline_type);
