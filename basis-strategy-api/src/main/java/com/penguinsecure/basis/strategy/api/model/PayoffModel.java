package com.penguinsecure.basis.strategy.api.model;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;

/** Compiled exact product-payoff contract registered by stable numeric ID. */
public interface PayoffModel {
    int modelId();

    boolean supports(InstrumentDefinition instrument);

    NumericStatus canonicalExposure(
            InstrumentDefinition instrument,
            long nativeQuantity,
            long price,
            int outputScale,
            RoundingPolicy rounding,
            MutableLongResult scratch,
            MutableLongResult result);

    NumericStatus nativeQuantity(
            InstrumentDefinition instrument,
            long canonicalExposure,
            int exposureScale,
            long price,
            RoundingPolicy rounding,
            MutableLongResult scratch,
            MutableLongResult result);

    NumericStatus riskValue(
            InstrumentDefinition instrument,
            long nativeQuantity,
            long price,
            int outputScale,
            RoundingPolicy rounding,
            MutableLongResult scratch,
            MutableLongResult result);
}
