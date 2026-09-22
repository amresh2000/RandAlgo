CREATE TABLE IF NOT EXISTS basis_journal_event (
    recording_id BIGINT NOT NULL,
    fragment_position BIGINT NOT NULL,
    event_sequence BIGINT NOT NULL,
    template_id INTEGER NOT NULL,
    schema_version INTEGER NOT NULL,
    event_type INTEGER NOT NULL,
    producer_id INTEGER NOT NULL,
    correlation_id BIGINT NOT NULL,
    venue_id INTEGER NOT NULL,
    account_id INTEGER NOT NULL,
    instrument_id BIGINT NOT NULL,
    strategy_id BIGINT NOT NULL,
    payload BYTEA NOT NULL,
    PRIMARY KEY (recording_id, fragment_position)
);

CREATE INDEX IF NOT EXISTS basis_journal_event_sequence_idx
    ON basis_journal_event (event_sequence);
CREATE INDEX IF NOT EXISTS basis_journal_route_idx
    ON basis_journal_event (venue_id, account_id, instrument_id, strategy_id);

CREATE TABLE IF NOT EXISTS basis_projection_checkpoint (
    projection_name TEXT PRIMARY KEY,
    recording_id BIGINT NOT NULL,
    fragment_position BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
