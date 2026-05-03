alter table live_match_tracking
    add column last_rich_timeline_refresh_at timestamp with time zone,
    add column last_rich_timeline_payload_id uuid references raw_payloads(id);

create index ix_live_match_tracking_rich_refresh
    on live_match_tracking(active, last_rich_timeline_refresh_at);
