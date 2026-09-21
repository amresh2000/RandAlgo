package com.penguinsecure.basis.strategy.basis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.core.product.InstrumentLifecycle;
import com.penguinsecure.basis.core.product.ProductFamily;
import org.junit.jupiter.api.Test;

final class PayoffModelsTest {
    @Test
    void inverseExposureAndNativeQuantityRoundTripConservatively() {
        InstrumentDefinition instrument =
                new InstrumentDefinition(
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
                        1,
                        0,
                        1);
        InversePayoffModel model = new InversePayoffModel(1);
        MutableLongResult scratch = new MutableLongResult();
        MutableLongResult result = new MutableLongResult();

        assertEquals(
                NumericStatus.OK,
                model.nativeQuantity(
                        instrument,
                        2_000_000,
                        8,
                        5_000_000,
                        RoundingPolicy.TOWARD_ZERO,
                        scratch,
                        result));
        assertEquals(1_000, result.value());
        assertEquals(
                NumericStatus.OK,
                model.canonicalExposure(
                        instrument,
                        result.value(),
                        5_000_000,
                        8,
                        RoundingPolicy.FLOOR,
                        scratch,
                        result));
        assertEquals(2_000_000, result.value());
    }
}
