package com.penguinsecure.basis.core.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.LongRange;

@Tag("property")
final class PayoffMathProperties {
    @Property(tries = 1_000)
    void linearPayoffMatchesBigDecimalOracle(
            @ForAll @LongRange(min = 1, max = 10_000_000) long price,
            @ForAll @LongRange(min = -1_000_000, max = 1_000_000) long quantity,
            @ForAll @LongRange(min = 1, max = 10_000) long multiplier) {
        MutableLongResult scratch = new MutableLongResult();
        MutableLongResult result = new MutableLongResult();

        NumericStatus status =
                PayoffMath.linearQuoteAmount(
                        price,
                        2,
                        quantity,
                        3,
                        multiplier,
                        2,
                        4,
                        RoundingPolicy.TOWARD_ZERO,
                        scratch,
                        result);

        BigDecimal expected =
                BigDecimal.valueOf(price, 2)
                        .multiply(BigDecimal.valueOf(quantity, 3))
                        .multiply(BigDecimal.valueOf(multiplier, 2))
                        .setScale(4, RoundingMode.DOWN);
        assertEquals(NumericStatus.OK, status);
        assertEquals(expected.unscaledValue().longValueExact(), result.value());
    }
}
