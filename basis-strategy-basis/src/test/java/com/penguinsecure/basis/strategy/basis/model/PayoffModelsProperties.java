package com.penguinsecure.basis.strategy.basis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.core.product.InstrumentLifecycle;
import com.penguinsecure.basis.core.product.ProductFamily;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.LongRange;

@Tag("property")
final class PayoffModelsProperties {
    private static final int OUTPUT_SCALE = 8;

    @Property(tries = 1_000)
    void inverseExposureMatchesBigDecimalOracle(
            @ForAll @LongRange(min = 1, max = 1_000_000) long quantity,
            @ForAll @LongRange(min = 100, max = 10_000_000) long price,
            @ForAll @LongRange(min = 1, max = 1_000) long contractSize) {
        InstrumentDefinition instrument = inverseInstrument(contractSize);
        MutableLongResult scratch = new MutableLongResult();
        MutableLongResult result = new MutableLongResult();

        NumericStatus status =
                new InversePayoffModel(1)
                        .canonicalExposure(
                                instrument,
                                quantity,
                                price,
                                OUTPUT_SCALE,
                                RoundingPolicy.FLOOR,
                                scratch,
                                result);

        BigDecimal expected =
                BigDecimal.valueOf(quantity)
                        .multiply(BigDecimal.valueOf(contractSize))
                        .divide(BigDecimal.valueOf(price, 2), OUTPUT_SCALE, RoundingMode.FLOOR);
        assertEquals(NumericStatus.OK, status);
        assertEquals(expected.unscaledValue().longValueExact(), result.value());
    }

    private static InstrumentDefinition inverseInstrument(final long contractSize) {
        return new InstrumentDefinition(
                101,
                1,
                ProductFamily.INVERSE_PERPETUAL,
                InstrumentLifecycle.TRADING,
                1,
                2,
                1,
                1,
                2,
                0,
                0,
                1,
                1,
                1,
                1_000_000,
                contractSize,
                0,
                1);
    }
}
