package com.penguinsecure.basis.strategy.basis.model;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;
import com.penguinsecure.basis.core.product.InstrumentDefinition;
import com.penguinsecure.basis.core.product.PayoffMath;
import com.penguinsecure.basis.strategy.api.model.PayoffModel;

/** Certified inverse perpetual/future payoff in explicit scaled units. */
public final class InversePayoffModel implements PayoffModel {
    private final int modelId;

    public InversePayoffModel(final int modelId) {
        if (modelId <= 0) throw new IllegalArgumentException("modelId must be positive");
        this.modelId = modelId;
    }

    @Override
    public int modelId() {
        return modelId;
    }

    @Override
    public boolean supports(final InstrumentDefinition instrument) {
        return instrument != null && instrument.productFamily().isInverse();
    }

    @Override
    public NumericStatus canonicalExposure(
            final InstrumentDefinition instrument,
            final long nativeQuantity,
            final long price,
            final int outputScale,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (!supports(instrument) || nativeQuantity < 0) {
            return result.fail(NumericStatus.MALFORMED).status();
        }
        return PayoffMath.inverseBaseExposure(
                nativeQuantity,
                instrument.quantityScale(),
                instrument.contractMultiplier(),
                instrument.multiplierScale(),
                price,
                instrument.priceScale(),
                outputScale,
                rounding,
                scratch,
                result);
    }

    @Override
    public NumericStatus nativeQuantity(
            final InstrumentDefinition instrument,
            final long canonicalExposure,
            final int exposureScale,
            final long price,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (!supports(instrument) || canonicalExposure < 0 || price <= 0) {
            return result.fail(NumericStatus.MALFORMED).status();
        }
        if (CheckedDecimalMath.multiply(canonicalExposure, price, scratch) != NumericStatus.OK) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        final int shift =
                instrument.multiplierScale()
                        + instrument.quantityScale()
                        - exposureScale
                        - instrument.priceScale();
        if (shift < -18 || shift > 18) {
            return result.fail(NumericStatus.SCALE_OUT_OF_RANGE).status();
        }
        long numerator = scratch.value();
        long divisor = instrument.contractMultiplier();
        if (shift >= 0) {
            if (CheckedDecimalMath.multiply(numerator, CheckedDecimalMath.powerOfTen(shift), result)
                    != NumericStatus.OK) return NumericStatus.OVERFLOW;
            numerator = result.value();
        } else {
            if (CheckedDecimalMath.multiply(divisor, CheckedDecimalMath.powerOfTen(-shift), result)
                    != NumericStatus.OK) return NumericStatus.OVERFLOW;
            divisor = result.value();
        }
        return CheckedDecimalMath.divide(numerator, divisor, rounding, result);
    }

    @Override
    public NumericStatus riskValue(
            final InstrumentDefinition instrument,
            final long nativeQuantity,
            final long price,
            final int outputScale,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (!supports(instrument) || nativeQuantity < 0) {
            return result.fail(NumericStatus.MALFORMED).status();
        }
        if (CheckedDecimalMath.multiply(nativeQuantity, instrument.contractMultiplier(), scratch)
                != NumericStatus.OK) return result.fail(NumericStatus.OVERFLOW).status();
        return CheckedDecimalMath.rescale(
                scratch.value(),
                instrument.quantityScale() + instrument.multiplierScale(),
                outputScale,
                rounding,
                result);
    }
}
