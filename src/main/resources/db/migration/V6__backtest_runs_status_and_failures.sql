alter table backtest_runs
    add column status varchar(40) not null default 'COMPLETED',
    add column requested_match_count integer not null default 0,
    add column processed_match_count integer not null default 0,
    add column failed_match_count integer not null default 0,
    add column failure_json jsonb not null default '[]'::jsonb;

create index ix_backtest_runs_status
    on backtest_runs(status);
