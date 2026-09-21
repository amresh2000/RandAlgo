package com.penguinsecure.basis.strategy.basis.model;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.strategy.api.model.HedgeRatioModel;
import com.penguinsecure.basis.strategy.api.model.PayoffModel;

/** Rounds native quantities down to venue lots so matching never increases exposure. */
public final class ConservativeHedgeRatioModel implements HedgeRatioModel {
    private final int modelId;

    public ConservativeHedgeRatioModel(final int modelId) {
        if (modelId <= 0) throw new IllegalArgumentException("modelId must be positive");
        this.modelId = modelId;
    }

    @Override
    public int modelId() {
        return modelId;
    }

    @Override
    public NumericStatus nativeQuantity(
            final PayoffModel payoffModel,
            final InstrumentDefinition instrument,
            final long canonicalExposure,
            final int exposureScale,
            final long price,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (payoffModel == null || instrument == null || canonicalExposure <= 0) {
            return result.fail(NumericStatus.MALFORMED).status();
        }
        if (payoffModel.nativeQuantity(
                        instrument,
                        canonicalExposure,
                        exposureScale,
                        price,
                        RoundingPolicy.TOWARD_ZERO,
                        scratch,
                        result)
                != NumericStatus.OK) return result.status();
        final long raw = result.value();
        if (CheckedDecimalMath.divide(raw, instrument.lotSize(), RoundingPolicy.FLOOR, scratch)
                        != NumericStatus.OK
                || CheckedDecimalMath.multiply(scratch.value(), instrument.lotSize(), result)
                        != NumericStatus.OK) return result.status();
        if (result.value() < instrument.minimumQuantity()
                || result.value() > instrument.maximumQuantity()) {
            return result.fail(NumericStatus.MALFORMED).status();
        }
        return NumericStatus.OK;
    }
}
