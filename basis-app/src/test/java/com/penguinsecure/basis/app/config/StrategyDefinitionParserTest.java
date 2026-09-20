package com.penguinsecure.basis.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.penguinsecure.basis.strategy.api.definition.BasisStrategyDefinition;
import com.penguinsecure.basis.strategy.api.definition.StrategyLifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class StrategyDefinitionParserTest {
    private final StrategyDefinitionParser parser = new StrategyDefinitionParser();

    @Test
    void parsesAndHashesDraftDefinition() throws Exception {
        byte[] json = resource("/definitions/inverse-perpetual-draft.json");

        BasisStrategyDefinition definition = parser.parse(json);

        assertEquals(1, definition.strategyId());
        assertEquals(StrategyLifecycle.DRAFT, definition.lifecycle());
        assertEquals(101, definition.firstLeg().instrumentId());
        assertEquals(201, definition.secondLeg().instrumentId());
        assertNotEquals(0, definition.configurationHashHigh());
        assertNotEquals(0, definition.configurationHashLow());
    }

    @Test
    void acceptsRepresentativeInverseLinearAndDatedDrafts() throws Exception {
        assertEquals(
                1,
                parser.parse(resource("/definitions/inverse-perpetual-draft.json")).strategyId());
        assertEquals(
                2, parser.parse(resource("/definitions/linear-perpetual-draft.json")).strategyId());
        assertEquals(
                3, parser.parse(resource("/definitions/dated-future-draft.json")).strategyId());
        assertThrows(
                StrategyDefinitionParseException.class,
                () -> parser.parse(resource("/definitions/rejected-unknown-field.json")));
    }

    @Test
    void canonicalHashIgnoresJsonWhitespace() throws Exception {
        byte[] original = resource("/definitions/inverse-perpetual-draft.json");
        byte[] compact =
                new String(original, StandardCharsets.UTF_8)
                        .replaceAll("\\s+", "")
                        .getBytes(StandardCharsets.UTF_8);

        BasisStrategyDefinition first = parser.parse(original);
        BasisStrategyDefinition second = parser.parse(compact);

        assertEquals(first.configurationHashHigh(), second.configurationHashHigh());
        assertEquals(first.configurationHashLow(), second.configurationHashLow());
    }

    @Test
    void rejectsUnknownDuplicateMissingAndStructurallyInvalidFields() throws Exception {
        String valid =
                new String(
                        resource("/definitions/inverse-perpetual-draft.json"),
                        StandardCharsets.UTF_8);

        assertThrows(
                StrategyDefinitionParseException.class,
                () ->
                        parser.parse(
                                valid.replaceFirst("\\{", "{\"surprise\":1,")
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                StrategyDefinitionParseException.class,
                () ->
                        parser.parse(
                                valid.replaceFirst(
                                                "\"strategyId\": 1,",
                                                "\"strategyId\": 1,\"strategyId\": 2,")
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                StrategyDefinitionParseException.class,
                () ->
                        parser.parse(
                                valid.replaceFirst("\"strategyId\": 1,", "")
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                StrategyDefinitionParseException.class,
                () ->
                        parser.parse(
                                valid.replace("\"entryThreshold\": 100", "\"entryThreshold\": 10")
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                StrategyDefinitionParseException.class,
                () ->
                        parser.parse(
                                valid.replace("\"strategyId\": 1", "\"strategyId\": 1.5")
                                        .getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = StrategyDefinitionParserTest.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing resource: " + name);
            return input.readAllBytes();
        }
    }
}
