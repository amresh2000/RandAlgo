package com.penguinsecure.basis.core.product;

import com.penguinsecure.basis.core.numeric.CheckedDecimalMath;
import com.penguinsecure.basis.core.numeric.MutableLongResult;
import com.penguinsecure.basis.core.numeric.NumericStatus;
import com.penguinsecure.basis.core.numeric.RoundingPolicy;

/** Exact primitive payoff and exposure calculations for certified product models. */
public final class PayoffMath {
    private PayoffMath() {}

    public static NumericStatus linearQuoteAmount(
            final long price,
            final int priceScale,
            final long quantity,
            final int quantityScale,
            final long multiplier,
            final int multiplierScale,
            final int outputScale,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (CheckedDecimalMath.multiply(price, quantity, scratch) != NumericStatus.OK) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        if (CheckedDecimalMath.multiply(scratch.value(), multiplier, result) != NumericStatus.OK) {
            return NumericStatus.OVERFLOW;
        }
        return CheckedDecimalMath.rescale(
                result.value(),
                priceScale + quantityScale + multiplierScale,
                outputScale,
                rounding,
                result);
    }

    public static NumericStatus inverseBaseExposure(
            final long nativeQuantity,
            final int quantityScale,
            final long contractSizeQuote,
            final int contractScale,
            final long price,
            final int priceScale,
            final int outputScale,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (price <= 0) {
            return result.fail(NumericStatus.MALFORMED).status();
        }
        final int scaleShift = priceScale + outputScale - quantityScale - contractScale;
        if (scaleShift < -18 || scaleShift > 18) {
            return result.fail(NumericStatus.SCALE_OUT_OF_RANGE).status();
        }
        long numeratorMultiplier = contractSizeQuote;
        long denominator = price;
        if (scaleShift >= 0) {
            if (CheckedDecimalMath.multiply(
                            numeratorMultiplier, CheckedDecimalMath.powerOfTen(scaleShift), scratch)
                    != NumericStatus.OK) {
                return result.fail(NumericStatus.OVERFLOW).status();
            }
            numeratorMultiplier = scratch.value();
        } else {
            if (CheckedDecimalMath.multiply(
                            denominator, CheckedDecimalMath.powerOfTen(-scaleShift), scratch)
                    != NumericStatus.OK) {
                return result.fail(NumericStatus.OVERFLOW).status();
            }
            denominator = scratch.value();
        }
        return CheckedDecimalMath.multiplyDivide(
                nativeQuantity, numeratorMultiplier, denominator, rounding, scratch, result);
    }

    public static NumericStatus rateAmount(
            final long amount,
            final int amountScale,
            final long rate,
            final int rateScale,
            final int outputScale,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (CheckedDecimalMath.multiply(amount, rate, scratch) != NumericStatus.OK) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        return CheckedDecimalMath.rescale(
                scratch.value(), amountScale + rateScale, outputScale, rounding, result);
    }

    public static NumericStatus convertCurrency(
            final long amount,
            final int amountScale,
            final long conversionRate,
            final int rateScale,
            final int outputScale,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        return rateAmount(
                amount,
                amountScale,
                conversionRate,
                rateScale,
                outputScale,
                rounding,
                scratch,
                result);
    }

    public static boolean isExpired(final long evaluationEpochNanos, final long expiryEpochNanos) {
        return expiryEpochNanos > 0 && evaluationEpochNanos >= expiryEpochNanos;
    }
}
