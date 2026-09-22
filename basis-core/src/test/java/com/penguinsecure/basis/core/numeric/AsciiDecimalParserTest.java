package com.penguinsecure.basis.core.numeric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class AsciiDecimalParserTest {
    private final MutableLongResult result = new MutableLongResult();

    @Test
    void parsesSignedScaledValuesAndCanonicalizesNegativeZero() {
        assertParsed("123.45", 2, 12_345);
        assertParsed("+7", 3, 7_000);
        assertParsed("-0.00", 2, 0);
        assertParsed("-9223372036854775808", 0, Long.MIN_VALUE);
        assertParsed("9223372036854775807", 0, Long.MAX_VALUE);
    }

    @Test
    void rejectsMalformedOrLossyForms() {
        assertStatus("", 2, NumericStatus.EMPTY);
        assertStatus("-", 2, NumericStatus.MALFORMED);
        assertStatus(".1", 2, NumericStatus.MALFORMED);
        assertStatus("1.", 2, NumericStatus.MALFORMED);
        assertStatus("1e2", 2, NumericStatus.MALFORMED);
        assertStatus(" 1", 2, NumericStatus.MALFORMED);
        assertStatus("1.234", 2, NumericStatus.SCALE_LOSS);
        assertStatus("9223372036854775808", 0, NumericStatus.OVERFLOW);
        assertStatus("-9223372036854775809", 0, NumericStatus.OVERFLOW);
    }

    private void assertParsed(String text, int scale, long expected) {
        assertEquals(NumericStatus.OK, parse(text, scale));
        assertEquals(expected, result.value());
    }

    private void assertStatus(String text, int scale, NumericStatus expected) {
        assertEquals(expected, parse(text, scale));
        assertEquals(0, result.value());
    }

    private NumericStatus parse(String text, int scale) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return AsciiDecimalParser.parse(bytes, 0, bytes.length, scale, result);
    }
}
