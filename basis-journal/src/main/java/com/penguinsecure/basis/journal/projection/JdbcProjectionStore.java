package com.penguinsecure.basis.journal.projection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;

/** pgJDBC append-only projection. Failures are contained and returned without credentials. */
public final class JdbcProjectionStore implements ProjectionStore {
    private static final String INSERT_EVENT =
            """
            INSERT INTO basis_journal_event (
              recording_id, fragment_position, event_sequence, template_id, schema_version,
              event_type, producer_id, correlation_id, venue_id, account_id,
              instrument_id, strategy_id, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (recording_id, fragment_position) DO NOTHING
            """;
    private static final String UPSERT_CHECKPOINT =
            """
            INSERT INTO basis_projection_checkpoint (
              projection_name, recording_id, fragment_position, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (projection_name) DO UPDATE SET
              recording_id = EXCLUDED.recording_id,
              fragment_position = EXCLUDED.fragment_position,
              updated_at = CURRENT_TIMESTAMP
            """;
    private final DataSource dataSource;
    private final int maximumPayloadBytes;

    public JdbcProjectionStore(final DataSource dataSource, final int maximumPayloadBytes) {
        if (dataSource == null || maximumPayloadBytes <= 0) {
            throw new IllegalArgumentException("data source and payload bound required");
        }
        this.dataSource = dataSource;
        this.maximumPayloadBytes = maximumPayloadBytes;
    }

    @Override
    public ProjectionResult appendBatch(
            final String projectionName,
            final List<ProjectionEvent> events,
            final ProjectionCheckpoint checkpoint) {
        if (projectionName == null
                || projectionName.isBlank()
                || events == null
                || events.isEmpty()
                || checkpoint == null
                || !projectionName.equals(checkpoint.projectionName())) {
            return ProjectionResult.INVALID_EVENT;
        }
        for (ProjectionEvent event : events) {
            if (event == null || event.payload().length > maximumPayloadBytes) {
                return ProjectionResult.INVALID_EVENT;
            }
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_EVENT);
                    PreparedStatement update = connection.prepareStatement(UPSERT_CHECKPOINT)) {
                for (ProjectionEvent event : events) {
                    bind(insert, event);
                    insert.addBatch();
                }
                insert.executeBatch();
                update.setString(1, projectionName);
                update.setLong(2, checkpoint.recordingId());
                update.setLong(3, checkpoint.fragmentPosition());
                update.executeUpdate();
                connection.commit();
                return ProjectionResult.COMMITTED;
            } catch (SQLException exception) {
                rollback(connection);
                return ProjectionResult.RETRYABLE_FAILURE;
            }
        } catch (SQLException exception) {
            return ProjectionResult.RETRYABLE_FAILURE;
        }
    }

    @Override
    public ProjectionResult rebuild(final boolean explicitlyAuthorized) {
        if (!explicitlyAuthorized) return ProjectionResult.UNAUTHORIZED_REBUILD;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("TRUNCATE TABLE basis_journal_event, basis_projection_checkpoint");
            connection.commit();
            return ProjectionResult.COMMITTED;
        } catch (SQLException exception) {
            return ProjectionResult.RETRYABLE_FAILURE;
        }
    }

    private static void bind(final PreparedStatement statement, final ProjectionEvent event)
            throws SQLException {
        statement.setLong(1, event.recordingId());
        statement.setLong(2, event.fragmentPosition());
        statement.setLong(3, event.eventSequence());
        statement.setInt(4, event.templateId());
        statement.setInt(5, event.schemaVersion());
        statement.setInt(6, event.eventType());
        statement.setInt(7, event.producerId());
        statement.setLong(8, event.correlationId());
        statement.setInt(9, event.venueId());
        statement.setInt(10, event.accountId());
        statement.setLong(11, event.instrumentId());
        statement.setLong(12, event.strategyId());
        statement.setBytes(13, event.payload());
    }

    private static void rollback(final Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            /* original failure wins */
        }
    }
}
