package com.penguinsecure.basis.core.numeric;

/** Checked arithmetic for scaled-long domain values. */
public final class CheckedDecimalMath {
    private static final long[] POWERS_OF_TEN = {
        1L,
        10L,
        100L,
        1_000L,
        10_000L,
        100_000L,
        1_000_000L,
        10_000_000L,
        100_000_000L,
        1_000_000_000L,
        10_000_000_000L,
        100_000_000_000L,
        1_000_000_000_000L,
        10_000_000_000_000L,
        100_000_000_000_000L,
        1_000_000_000_000_000L,
        10_000_000_000_000_000L,
        100_000_000_000_000_000L,
        1_000_000_000_000_000_000L
    };

    private CheckedDecimalMath() {}

    public static long powerOfTen(final int decimalPlaces) {
        if (decimalPlaces < 0 || decimalPlaces >= POWERS_OF_TEN.length) {
            throw new IllegalArgumentException("decimalPlaces must be in [0, 18]");
        }
        return POWERS_OF_TEN[decimalPlaces];
    }

    public static NumericStatus add(
            final long left, final long right, final MutableLongResult result) {
        final long value = left + right;
        if (((left ^ value) & (right ^ value)) < 0) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        return result.set(value).status();
    }

    public static NumericStatus subtract(
            final long left, final long right, final MutableLongResult result) {
        final long value = left - right;
        if (((left ^ right) & (left ^ value)) < 0) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        return result.set(value).status();
    }

    public static NumericStatus multiply(
            final long left, final long right, final MutableLongResult result) {
        if (left == 0 || right == 0) {
            return result.set(0).status();
        }
        if ((left == Long.MIN_VALUE && right == -1) || (right == Long.MIN_VALUE && left == -1)) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        final long value = left * right;
        if (value / right != left) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        return result.set(value).status();
    }

    public static NumericStatus rescale(
            final long value,
            final int fromScale,
            final int toScale,
            final RoundingPolicy rounding,
            final MutableLongResult result) {
        if (fromScale < 0
                || fromScale > DecimalScale.MAX_DECIMAL_PLACES
                || toScale < 0
                || toScale > DecimalScale.MAX_DECIMAL_PLACES) {
            return result.fail(NumericStatus.SCALE_OUT_OF_RANGE).status();
        }
        if (fromScale == toScale) {
            return result.set(value).status();
        }
        if (toScale > fromScale) {
            return multiply(value, powerOfTen(toScale - fromScale), result);
        }
        return divide(value, powerOfTen(fromScale - toScale), rounding, result);
    }

    public static NumericStatus divide(
            final long dividend,
            final long divisor,
            final RoundingPolicy rounding,
            final MutableLongResult result) {
        if (divisor == 0) {
            return result.fail(NumericStatus.DIVIDE_BY_ZERO).status();
        }
        if (dividend == Long.MIN_VALUE && divisor == -1) {
            return result.fail(NumericStatus.OVERFLOW).status();
        }
        final long quotient = dividend / divisor;
        final long remainder = dividend % divisor;
        if (remainder == 0) {
            return result.set(quotient).status();
        }
        if (rounding == RoundingPolicy.EXACT) {
            return result.fail(NumericStatus.SCALE_LOSS).status();
        }

        final boolean positive = (dividend ^ divisor) >= 0;
        final long adjustment =
                switch (rounding) {
                    case TOWARD_ZERO -> 0;
                    case AWAY_FROM_ZERO -> positive ? 1 : -1;
                    case FLOOR -> positive ? 0 : -1;
                    case CEILING -> positive ? 1 : 0;
                    case EXACT -> throw new AssertionError("handled above");
                };
        return add(quotient, adjustment, result);
    }

    public static NumericStatus multiplyDivide(
            final long value,
            final long multiplier,
            final long divisor,
            final RoundingPolicy rounding,
            final MutableLongResult scratch,
            final MutableLongResult result) {
        if (divisor == 0) {
            return result.fail(NumericStatus.DIVIDE_BY_ZERO).status();
        }
        long reducedValue = value;
        long reducedMultiplier = multiplier;
        long reducedDivisor = divisor;

        final long valueGcd = greatestCommonDivisor(reducedValue, reducedDivisor);
        reducedValue /= valueGcd;
        reducedDivisor /= valueGcd;
        final long multiplierGcd = greatestCommonDivisor(reducedMultiplier, reducedDivisor);
        reducedMultiplier /= multiplierGcd;
        reducedDivisor /= multiplierGcd;

        if (multiply(reducedValue, reducedMultiplier, scratch) != NumericStatus.OK) {
            if (reducedValue < 0 || reducedMultiplier < 0 || reducedDivisor < 0) {
                return result.fail(NumericStatus.OVERFLOW).status();
            }
            return dividePositive128(
                    reducedValue, reducedMultiplier, reducedDivisor, rounding, result);
        }
        return divide(scratch.value(), reducedDivisor, rounding, result);
    }

    private static NumericStatus dividePositive128(
            final long left,
            final long right,
            final long divisor,
            final RoundingPolicy rounding,
            final MutableLongResult result) {
        final long high = Math.multiplyHigh(left, right);
        final long low = left * right;
        if (high >= divisor) return result.fail(NumericStatus.OVERFLOW).status();

        long remainder = high;
        long quotient = 0;
        for (int bit = Long.SIZE - 1; bit >= 0; bit--) {
            remainder = (remainder << 1) | ((low >>> bit) & 1L);
            if (Long.compareUnsigned(remainder, divisor) >= 0) {
                remainder -= divisor;
                quotient |= 1L << bit;
            }
        }
        if (quotient < 0) return result.fail(NumericStatus.OVERFLOW).status();
        if (remainder == 0) return result.set(quotient).status();
        if (rounding == RoundingPolicy.EXACT) {
            return result.fail(NumericStatus.SCALE_LOSS).status();
        }
        return switch (rounding) {
            case TOWARD_ZERO, FLOOR -> result.set(quotient).status();
            case AWAY_FROM_ZERO, CEILING -> add(quotient, 1, result);
            case EXACT -> throw new AssertionError("handled above");
        };
    }

    private static long greatestCommonDivisor(final long left, final long right) {
        if (left == Long.MIN_VALUE || right == Long.MIN_VALUE) {
            return 1;
        }
        long a = Math.abs(left);
        long b = Math.abs(right);
        while (b != 0) {
            final long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a == 0 ? 1 : a;
    }
}
