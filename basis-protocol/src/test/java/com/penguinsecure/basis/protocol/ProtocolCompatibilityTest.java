package com.penguinsecure.basis.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.protocol.sbe.EventHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.EventType;
import com.penguinsecure.basis.protocol.sbe.InstrumentDefinitionDecoder;
import com.penguinsecure.basis.protocol.sbe.InstrumentDefinitionEncoder;
import com.penguinsecure.basis.protocol.sbe.LifecycleState;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderDecoder;
import com.penguinsecure.basis.protocol.sbe.MessageHeaderEncoder;
import com.penguinsecure.basis.protocol.sbe.ProductFamily;
import com.penguinsecure.basis.protocol.sbe.Venue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.HexFormat;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ProtocolCompatibilityTest {
    private static final HexFormat HEX = HexFormat.of();
    private static final int V0_BLOCK_LENGTH = 154;

    @Test
    void currentEncodingMatchesGoldenFrame() throws IOException {
        byte[] encoded = encodeCurrent();

        assertEquals(golden("/sbe/v1/instrument-definition.hex"), HEX.formatHex(encoded));

        InstrumentDefinitionDecoder decoder = decode(encoded);
        assertEquals(ProductFamily.INVERSE_PERPETUAL, decoder.productFamily());
        assertEquals(100_000_000L, decoder.contractMultiplier());
        assertEquals(0x0102030405060708L, decoder.metadataHashHigh());
        assertEquals(0x1112131415161718L, decoder.metadataHashLow());
        assertEquals(8, decoder.multiplierScale());
        assertEquals(31, decoder.feeSourceId());
    }

    @Test
    void currentDecoderReadsPreviousSchemaVersion() throws IOException {
        byte[] previous = previousVersionFrame();

        assertEquals(golden("/sbe/v0/instrument-definition.hex"), HEX.formatHex(previous));

        InstrumentDefinitionDecoder decoder = decode(previous);
        assertEquals(0, decoder.actingVersion());
        assertEquals(ProductFamily.INVERSE_PERPETUAL, decoder.productFamily());
        assertEquals(
                InstrumentDefinitionDecoder.metadataHashHighNullValue(),
                decoder.metadataHashHigh());
        assertEquals(
                InstrumentDefinitionDecoder.metadataHashLowNullValue(), decoder.metadataHashLow());
        assertEquals(
                InstrumentDefinitionDecoder.multiplierScaleNullValue(), decoder.multiplierScale());
        assertEquals(InstrumentDefinitionDecoder.feeSourceIdNullValue(), decoder.feeSourceId());
    }

    private static byte[] encodeCurrent() {
        byte[] bytes =
                new byte
                        [MessageHeaderEncoder.ENCODED_LENGTH
                                + InstrumentDefinitionEncoder.BLOCK_LENGTH];
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        InstrumentDefinitionEncoder encoder =
                new InstrumentDefinitionEncoder()
                        .wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder());

        EventHeaderEncoder event = encoder.eventHeader();
        event.eventType(EventType.INSTRUMENT_DEFINITION)
                .eventSequence(17)
                .producerId(3)
                .producerEpoch(5)
                .cellId(7)
                .venue(Venue.BYBIT)
                .accountId(11)
                .instrumentId(13)
                .strategyId(0)
                .configurationGeneration(19)
                .sessionGeneration(23)
                .correlationId(29)
                .exchangeEpochNanos(31)
                .localReceiveEpochNanos(37)
                .localReceiveMonoNanos(41)
                .causeEventSequence(43)
                .flags(47)
                .reasonCode(0);

        encoder.productFamily(ProductFamily.INVERSE_PERPETUAL)
                .lifecycle(LifecycleState.TRADING)
                .baseCurrencyId(1)
                .quoteCurrencyId(2)
                .settlementCurrencyId(1)
                .collateralCurrencyId(1)
                .priceScale((short) 1)
                .quantityScale((short) 0)
                .tickSize(1)
                .lotSize(1)
                .minimumQuantity(1)
                .maximumQuantity(1_000_000)
                .contractMultiplier(100_000_000L)
                .expiryEpochNanos(0)
                .metadataHashHigh(0x0102030405060708L)
                .metadataHashLow(0x1112131415161718L)
                .multiplierScale((short) 8)
                .feeSourceId(31);
        return bytes;
    }

    private static byte[] previousVersionFrame() {
        byte[] current = encodeCurrent();
        byte[] previous = new byte[MessageHeaderEncoder.ENCODED_LENGTH + V0_BLOCK_LENGTH];
        System.arraycopy(current, 0, previous, 0, previous.length);
        UnsafeBuffer buffer = new UnsafeBuffer(previous);
        buffer.putShort(0, (short) V0_BLOCK_LENGTH, ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(6, (short) 0, ByteOrder.LITTLE_ENDIAN);
        return previous;
    }

    private static InstrumentDefinitionDecoder decode(byte[] encoded) {
        return new InstrumentDefinitionDecoder()
                .wrapAndApplyHeader(new UnsafeBuffer(encoded), 0, new MessageHeaderDecoder());
    }

    private static String golden(String resource) throws IOException {
        try (InputStream input = ProtocolCompatibilityTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing golden resource: " + resource);
            }
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)
                    .trim();
        }
    }
}
