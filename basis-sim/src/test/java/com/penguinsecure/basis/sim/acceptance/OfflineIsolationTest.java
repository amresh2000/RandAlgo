package com.penguinsecure.basis.sim.acceptance;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
final class OfflineIsolationTest {
    @Test
    void productionSimulatorHasNoNetworkDatabaseCredentialOrConcreteVenueDependency()
            throws IOException {
        Path root = Path.of(System.getProperty("basis.reactor.root"));
        Path source = root.resolve("basis-sim/src/main/java");
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    String content = Files.readString(path);
                                    assertFalse(
                                            content.contains("import java.net."), path.toString());
                                    assertFalse(
                                            content.contains("import java.sql."), path.toString());
                                    assertFalse(content.contains("System.getenv"), path.toString());
                                    assertFalse(
                                            content.contains("com.penguinsecure.basis.venue.bybit"),
                                            path.toString());
                                    assertFalse(
                                            content.contains(
                                                    "com.penguinsecure.basis.venue.deribit"),
                                            path.toString());
                                } catch (IOException exception) {
                                    throw new IllegalStateException(exception);
                                }
                            });
        }
    }
}
