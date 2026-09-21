package com.penguinsecure.basis.strategy.api.model;

import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;

/** Converts canonical exposure into conservative venue-native quantity. */
public interface HedgeRatioModel {
    int modelId();

    NumericStatus nativeQuantity(
            PayoffModel payoffModel,
            InstrumentDefinition instrument,
            long canonicalExposure,
            int exposureScale,
            long price,
            RoundingPolicy rounding,
            MutableLongResult scratch,
            MutableLongResult result);
}
