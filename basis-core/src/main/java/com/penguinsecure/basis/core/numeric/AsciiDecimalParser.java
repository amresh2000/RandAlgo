package com.penguinsecure.basis.core.numeric;

/** Parses the certified plain-decimal grammar directly from bytes into a scaled long. */
public final class AsciiDecimalParser {
    private AsciiDecimalParser() {}

    public static NumericStatus parse(
            final byte[] input,
            final int offset,
            final int length,
            final int targetScale,
            final MutableLongResult result) {
        if (input == null || result == null) {
            throw new NullPointerException("input and result are required");
        }
        if (targetScale < 0 || targetScale > DecimalScale.MAX_DECIMAL_PLACES) {
            return result.fail(NumericStatus.SCALE_OUT_OF_RANGE).status();
        }
        if (offset < 0 || length < 0 || offset > input.length - length) {
            throw new IndexOutOfBoundsException("invalid byte range");
        }
        if (length == 0) {
            return result.fail(NumericStatus.EMPTY).status();
        }

        final int end = offset + length;
        int index = offset;
        boolean negative = false;
        final byte first = input[index];
        if (first == '-' || first == '+') {
            negative = first == '-';
            index++;
            if (index == end) {
                return result.fail(NumericStatus.MALFORMED).status();
            }
        }

        final long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        final long multiplyLimit = limit / 10;
        long accumulated = 0;
        int fractionalDigits = 0;
        int integerDigits = 0;
        boolean decimalPoint = false;

        while (index < end) {
            final int character = input[index++] & 0xff;
            if (character == '.') {
                if (decimalPoint || integerDigits == 0 || index == end) {
                    return result.fail(NumericStatus.MALFORMED).status();
                }
                decimalPoint = true;
                continue;
            }
            if (character < '0' || character > '9') {
                return result.fail(NumericStatus.MALFORMED).status();
            }
            if (decimalPoint) {
                fractionalDigits++;
                if (fractionalDigits > targetScale) {
                    return result.fail(NumericStatus.SCALE_LOSS).status();
                }
            } else {
                integerDigits++;
            }

            final int digit = character - '0';
            if (accumulated < multiplyLimit) {
                return result.fail(NumericStatus.OVERFLOW).status();
            }
            accumulated *= 10;
            if (accumulated < limit + digit) {
                return result.fail(NumericStatus.OVERFLOW).status();
            }
            accumulated -= digit;
        }

        for (int missing = targetScale - fractionalDigits; missing > 0; missing--) {
            if (accumulated < multiplyLimit) {
                return result.fail(NumericStatus.OVERFLOW).status();
            }
            accumulated *= 10;
        }

        return result.set(negative ? accumulated : -accumulated).status();
    }
}
