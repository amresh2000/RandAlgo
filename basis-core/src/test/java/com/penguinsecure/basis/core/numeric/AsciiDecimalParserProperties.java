package com.penguinsecure.basis.core.numeric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

@Tag("property")
final class AsciiDecimalParserProperties {
    @Property(tries = 2_000)
    void roundTripsCanonicalScaledLongs(
            @ForAll @LongRange(min = -1_000_000_000_000L, max = 1_000_000_000_000L) long value,
            @ForAll @IntRange(min = 0, max = 6) int scale) {
        String text = java.math.BigDecimal.valueOf(value, scale).toPlainString();
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        MutableLongResult result = new MutableLongResult();

        assertEquals(
                NumericStatus.OK, AsciiDecimalParser.parse(bytes, 0, bytes.length, scale, result));
        assertEquals(value, result.value());
    }
}
