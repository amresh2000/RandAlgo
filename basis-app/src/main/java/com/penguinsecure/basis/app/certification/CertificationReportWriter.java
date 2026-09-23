package com.penguinsecure.basis.app.certification;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;

/** Deterministic, sanitized JSON serialization for review and archival. */
public final class CertificationReportWriter {
    private static final JsonFactory JSON = new JsonFactory();

    public void write(final CertificationBundle bundle, final OutputStream output)
            throws IOException {
        if (bundle == null || output == null) throw new NullPointerException();
        final CertificationGateResult result = new CertificationGate().evaluate(bundle);
        try (JsonGenerator json = JSON.createGenerator(output)) {
            json.writeStartObject();
            json.writeStringField("schema", "basis-phase-12-certification/v1");
            json.writeStringField("buildRevision", bundle.buildRevision());
            json.writeStringField("decision", result.decision().name());
            json.writeBooleanField(
                    "testnetLimitationsDocumented", bundle.testnetLimitationsDocumented());
            writeReasons(json, result);
            writeScenarios(json, bundle);
            writeCriteria(json, bundle);
            writeSoaks(json, bundle);
            writeFixtures(json, bundle);
            writeFindings(json, bundle);
            json.writeEndObject();
        }
    }

    private static void writeReasons(final JsonGenerator json, final CertificationGateResult result)
            throws IOException {
        json.writeArrayFieldStart("reasonCodes");
        for (String reason : result.reasonCodes()) json.writeString(reason);
        json.writeEndArray();
    }

    private static void writeScenarios(final JsonGenerator json, final CertificationBundle bundle)
            throws IOException {
        json.writeArrayFieldStart("scenarios");
        for (ScenarioEvidence evidence :
                bundle.scenarios().stream()
                        .sorted(
                                Comparator.comparing(
                                                (ScenarioEvidence value) ->
                                                        value.key().scenario().name())
                                        .thenComparing(value -> value.key().venue().name()))
                        .toList()) {
            json.writeStartObject();
            json.writeStringField("scenario", evidence.key().scenario().name());
            json.writeStringField("venue", evidence.key().venue().name());
            json.writeStringField("status", evidence.status().name());
            json.writeNumberField("startedEpochMillis", evidence.startedEpochMillis());
            json.writeNumberField("completedEpochMillis", evidence.completedEpochMillis());
            json.writeStringField("reasonCode", evidence.reasonCode());
            json.writeObjectFieldStart("verified");
            final StateVerification state = evidence.stateVerification();
            json.writeBooleanField("positions", state.positions());
            json.writeBooleanField("balances", state.balances());
            json.writeBooleanField("fills", state.fills());
            json.writeBooleanField("fees", state.fees());
            json.writeBooleanField("openOrders", state.openOrders());
            json.writeBooleanField("reservations", state.reservations());
            json.writeBooleanField("journal", state.journal());
            json.writeEndObject();
            writeHashes(json, evidence.artifactSha256());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static void writeCriteria(final JsonGenerator json, final CertificationBundle bundle)
            throws IOException {
        json.writeArrayFieldStart("acceptanceCriteria");
        for (AcceptanceEvidence evidence :
                bundle.acceptanceCriteria().stream()
                        .sorted(Comparator.comparing(value -> value.criterion().name()))
                        .toList()) {
            json.writeStartObject();
            json.writeStringField("criterion", evidence.criterion().name());
            json.writeStringField("status", evidence.status().name());
            writeHashes(json, evidence.artifactSha256());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static void writeSoaks(final JsonGenerator json, final CertificationBundle bundle)
            throws IOException {
        json.writeArrayFieldStart("soaks");
        for (SoakEvidence evidence :
                bundle.soaks().stream()
                        .sorted(Comparator.comparingLong(SoakEvidence::durationSeconds))
                        .toList()) {
            json.writeStartObject();
            json.writeNumberField("durationSeconds", evidence.durationSeconds());
            json.writeStringField("status", evidence.status().name());
            json.writeBooleanField("allocationAndJfr", evidence.allocationAndJfr());
            json.writeBooleanField("queue", evidence.queue());
            json.writeBooleanField("cpu", evidence.cpu());
            json.writeBooleanField("memory", evidence.memory());
            json.writeBooleanField("reconnect", evidence.reconnect());
            json.writeBooleanField("latency", evidence.latency());
            writeHashes(json, evidence.artifactSha256());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static void writeFixtures(final JsonGenerator json, final CertificationBundle bundle)
            throws IOException {
        json.writeArrayFieldStart("fixtures");
        for (FixtureFreezeEvidence evidence :
                bundle.fixtures().stream()
                        .sorted(Comparator.comparing(value -> value.venue().name()))
                        .toList()) {
            json.writeStartObject();
            json.writeStringField("venue", evidence.venue().name());
            json.writeStringField("status", evidence.status().name());
            json.writeStringField("manifestSha256", evidence.manifestSha256());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static void writeFindings(final JsonGenerator json, final CertificationBundle bundle)
            throws IOException {
        json.writeArrayFieldStart("findings");
        for (CertificationFinding finding :
                bundle.findings().stream()
                        .sorted(Comparator.comparing(CertificationFinding::id))
                        .toList()) {
            json.writeStartObject();
            json.writeStringField("id", finding.id());
            json.writeNumberField("severity", finding.severity());
            json.writeBooleanField("resolved", finding.resolved());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static void writeHashes(final JsonGenerator json, final Iterable<String> hashes)
            throws IOException {
        json.writeArrayFieldStart("artifactSha256");
        for (String hash : hashes) json.writeString(hash);
        json.writeEndArray();
    }
}
