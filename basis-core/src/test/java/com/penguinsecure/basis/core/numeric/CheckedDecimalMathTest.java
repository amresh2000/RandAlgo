package com.penguinsecure.basis.core.numeric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CheckedDecimalMathTest {
    private final MutableLongResult result = new MutableLongResult();

    @Test
    void checksOverflowAndScaleLoss() {
        assertEquals(NumericStatus.OVERFLOW, CheckedDecimalMath.add(Long.MAX_VALUE, 1, result));
        assertEquals(
                NumericStatus.OVERFLOW, CheckedDecimalMath.subtract(Long.MAX_VALUE, -1, result));
        assertEquals(
                NumericStatus.OVERFLOW, CheckedDecimalMath.subtract(Long.MIN_VALUE, 1, result));
        assertEquals(NumericStatus.OK, CheckedDecimalMath.subtract(10, 4, result));
        assertEquals(6, result.value());
        assertEquals(
                NumericStatus.OVERFLOW, CheckedDecimalMath.multiply(Long.MIN_VALUE, -1, result));
        assertEquals(
                NumericStatus.SCALE_LOSS,
                CheckedDecimalMath.rescale(101, 2, 1, RoundingPolicy.EXACT, result));
    }

    @Test
    void appliesExplicitSignedRounding() {
        assertRescale(101, RoundingPolicy.TOWARD_ZERO, 10);
        assertRescale(101, RoundingPolicy.AWAY_FROM_ZERO, 11);
        assertRescale(-101, RoundingPolicy.FLOOR, -11);
        assertRescale(-101, RoundingPolicy.CEILING, -10);
    }

    @Test
    void dividesAValidPositive128BitIntermediateWithoutAllocation() {
        MutableLongResult scratch = new MutableLongResult();

        assertEquals(
                NumericStatus.OK,
                CheckedDecimalMath.multiplyDivide(
                        1_000_000_000L,
                        10_000_000_000L,
                        101,
                        RoundingPolicy.FLOOR,
                        scratch,
                        result));
        assertEquals(99_009_900_990_099_009L, result.value());
    }

    private void assertRescale(long value, RoundingPolicy policy, long expected) {
        assertEquals(NumericStatus.OK, CheckedDecimalMath.rescale(value, 2, 1, policy, result));
        assertEquals(expected, result.value());
    }
}
