package com.penguinsecure.basis.core.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import org.junit.jupiter.api.Test;

final class PayoffMathTest {
    private final MutableLongResult scratch = new MutableLongResult();
    private final MutableLongResult result = new MutableLongResult();

    @Test
    void calculatesLinearQuoteAmountExactly() {
        NumericStatus status =
                PayoffMath.linearQuoteAmount(
                        500_000, 1, 100, 3, 1, 0, 2, RoundingPolicy.EXACT, scratch, result);

        assertEquals(NumericStatus.OK, status);
        assertEquals(500_000, result.value());
    }

    @Test
    void calculatesInverseBaseExposureExactly() {
        NumericStatus status =
                PayoffMath.inverseBaseExposure(
                        1_000, 0, 1, 0, 500_000, 1, 8, RoundingPolicy.EXACT, scratch, result);

        assertEquals(NumericStatus.OK, status);
        assertEquals(2_000_000, result.value());
    }

    @Test
    void calculatesFundingAndExpiryWithoutDefaults() {
        assertEquals(
                NumericStatus.OK,
                PayoffMath.rateAmount(
                        5_000_000, 2, 35, 5, 2, RoundingPolicy.EXACT, scratch, result));
        assertEquals(1_750, result.value());
        assertFalse(PayoffMath.isExpired(99, 100));
        assertTrue(PayoffMath.isExpired(100, 100));
    }
}
