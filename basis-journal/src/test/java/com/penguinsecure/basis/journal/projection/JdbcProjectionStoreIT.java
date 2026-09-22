package com.penguinsecure.basis.journal.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

@Tag("integration")
final class JdbcProjectionStoreIT {
    private PGSimpleDataSource dataSource;

    @AfterEach
    void clean() throws Exception {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "DROP TABLE IF EXISTS basis_journal_event, basis_projection_checkpoint");
        }
    }

    @Test
    void duplicateReplayAndAuthorizedRebuildAreDeterministic() throws Exception {
        final String url = System.getProperty("basis.test.postgres.url");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "PostgreSQL URL not supplied");
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        applySchema();
        JdbcProjectionStore store = new JdbcProjectionStore(dataSource, 1024);
        ProjectionEvent event =
                new ProjectionEvent(1, 64, 1, 19, 2, 19, 1, 2, 1, 1, 1, 1, new byte[] {1, 2});
        ProjectionCheckpoint checkpoint = new ProjectionCheckpoint("primary", 1, 64);

        assertEquals(
                ProjectionResult.COMMITTED,
                store.appendBatch("primary", List.of(event), checkpoint));
        assertEquals(
                ProjectionResult.COMMITTED,
                store.appendBatch("primary", List.of(event), checkpoint));
        assertEquals(1, count("basis_journal_event"));
        assertEquals(1, count("basis_projection_checkpoint"));
        assertEquals(ProjectionResult.COMMITTED, store.rebuild(true));
        assertEquals(0, count("basis_journal_event"));
        assertEquals(0, count("basis_projection_checkpoint"));
    }

    private void applySchema() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/db/phase-8-projection.sql")) {
            if (input == null) throw new IllegalStateException("projection schema missing");
            final String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                for (String command : schema.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }

    private int count(final String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }
}
