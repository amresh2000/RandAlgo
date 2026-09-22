package com.penguinsecure.basis.venue.api.json;

import com.penguinsecure.basis.venue.api.marketdata.MarketDataParseStatus;

/** Bounded JSON structural cursor. It never materializes strings or containers. */
public final class JsonByteCursor {
    private final int maximumDepth;
    private final int maximumTokenBytes;
    private ReadableBytes input;
    private int index;
    private int end;
    private MarketDataParseStatus status = MarketDataParseStatus.OK;

    public JsonByteCursor(final int maximumDepth, final int maximumTokenBytes) {
        if (maximumDepth <= 0 || maximumDepth > 64 || maximumTokenBytes <= 0) {
            throw new IllegalArgumentException("invalid JSON bounds");
        }
        this.maximumDepth = maximumDepth;
        this.maximumTokenBytes = maximumTokenBytes;
    }

    public void reset(final ReadableBytes input, final int offset, final int length) {
        if (input == null) throw new NullPointerException("input is required");
        if (offset < 0 || length < 0 || offset > Integer.MAX_VALUE - length) {
            throw new IndexOutOfBoundsException("invalid frame range");
        }
        this.input = input;
        index = offset;
        end = offset + length;
        status = MarketDataParseStatus.OK;
    }

    public MarketDataParseStatus status() {
        return status;
    }

    public int position() {
        return index;
    }

    public boolean atEnd() {
        skipWhitespace();
        return index == end;
    }

    public byte peek() {
        skipWhitespace();
        return index < end ? input.getByte(index) : 0;
    }

    public boolean consume(final byte expected) {
        skipWhitespace();
        if (index >= end || input.getByte(index) != expected)
            return fail(MarketDataParseStatus.MALFORMED);
        index++;
        return true;
    }

    public boolean readAsciiString(final ByteToken token) {
        skipWhitespace();
        if (index >= end || input.getByte(index++) != '"')
            return fail(MarketDataParseStatus.MALFORMED);
        final int start = index;
        while (index < end) {
            final int value = input.getByte(index) & 0xff;
            if (value == '"') {
                final int length = index++ - start;
                if (length > maximumTokenBytes) return fail(MarketDataParseStatus.TOKEN_TOO_LONG);
                token.set(start, length);
                return true;
            }
            if (value == '\\' || value < 0x20 || value > 0x7f) {
                return fail(MarketDataParseStatus.MALFORMED);
            }
            if (index - start >= maximumTokenBytes)
                return fail(MarketDataParseStatus.TOKEN_TOO_LONG);
            index++;
        }
        return fail(MarketDataParseStatus.MALFORMED);
    }

    public boolean tokenEquals(final ByteToken token, final String ascii) {
        if (ascii.length() != token.length()) return false;
        for (int i = 0; i < token.length(); i++) {
            if (input.getByte(token.offset() + i) != (byte) ascii.charAt(i)) return false;
        }
        return true;
    }

    public boolean tokenStartsWith(final ByteToken token, final String ascii) {
        if (ascii.length() > token.length()) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if (input.getByte(token.offset() + i) != (byte) ascii.charAt(i)) return false;
        }
        return true;
    }

    public boolean readPositiveLong(final MutableJsonLong target) {
        skipWhitespace();
        final int start = index;
        long value = 0;
        while (index < end) {
            final int digit = (input.getByte(index) & 0xff) - '0';
            if (digit < 0 || digit > 9) break;
            if (value > (Long.MAX_VALUE - digit) / 10)
                return fail(MarketDataParseStatus.INVALID_NUMBER);
            value = value * 10 + digit;
            index++;
        }
        if (index == start) return fail(MarketDataParseStatus.INVALID_NUMBER);
        target.value(value);
        return true;
    }

    public boolean readScaledDecimalString(final int scale, final MutableJsonLong target) {
        skipWhitespace();
        if (index >= end || input.getByte(index++) != '"')
            return fail(MarketDataParseStatus.MALFORMED);
        final int start = index;
        while (index < end && input.getByte(index) != '"') {
            if (index - start >= maximumTokenBytes)
                return fail(MarketDataParseStatus.TOKEN_TOO_LONG);
            index++;
        }
        if (index >= end) return fail(MarketDataParseStatus.MALFORMED);
        final int length = index++ - start;
        return parseScaled(start, length, scale, target);
    }

    public boolean readScaledDecimal(final int scale, final MutableJsonLong target) {
        return readScaledDecimal(scale, target, false);
    }

    /** Reads a JSON number and permits an exponent only when exact at the requested scale. */
    public boolean readScaledDecimalWithExponent(final int scale, final MutableJsonLong target) {
        return readScaledDecimal(scale, target, true);
    }

    private boolean readScaledDecimal(
            final int scale, final MutableJsonLong target, final boolean allowExponent) {
        skipWhitespace();
        final boolean quoted = index < end && input.getByte(index) == '"';
        if (quoted) index++;
        final int start = index;
        while (index < end) {
            final byte value = input.getByte(index);
            if ((quoted && value == '"')
                    || (!quoted
                            && (value == ','
                                    || value == ']'
                                    || value == '}'
                                    || isWhitespace(value)))) break;
            if (index - start >= maximumTokenBytes)
                return fail(MarketDataParseStatus.TOKEN_TOO_LONG);
            index++;
        }
        final int length = index - start;
        if (quoted && (index >= end || input.getByte(index++) != '"')) {
            return fail(MarketDataParseStatus.MALFORMED);
        }
        return parseScaled(start, length, scale, target, allowExponent);
    }

    public boolean skipValue() {
        skipWhitespace();
        return skipValue(0);
    }

    private boolean skipValue(final int depth) {
        if (depth > maximumDepth) return fail(MarketDataParseStatus.DEPTH_EXCEEDED);
        if (index >= end) return fail(MarketDataParseStatus.MALFORMED);
        final byte first = input.getByte(index);
        if (first == '"') return skipString();
        if (first == '{') return skipCompound((byte) '{', (byte) '}', depth);
        if (first == '[') return skipCompound((byte) '[', (byte) ']', depth);
        final int start = index;
        while (index < end) {
            final byte value = input.getByte(index);
            if (value == ',' || value == '}' || value == ']' || isWhitespace(value)) break;
            index++;
            if (index - start > maximumTokenBytes)
                return fail(MarketDataParseStatus.TOKEN_TOO_LONG);
        }
        return index > start;
    }

    private boolean skipCompound(final byte open, final byte close, final int depth) {
        index++;
        skipWhitespace();
        if (index < end && input.getByte(index) == close) {
            index++;
            return true;
        }
        while (index < end) {
            if (open == '{') {
                if (!skipString()) return false;
                if (!consume((byte) ':')) return false;
            }
            skipWhitespace();
            if (!skipValue(depth + 1)) return false;
            skipWhitespace();
            if (index < end && input.getByte(index) == close) {
                index++;
                return true;
            }
            if (!consume((byte) ',')) return false;
        }
        return fail(MarketDataParseStatus.MALFORMED);
    }

    private boolean skipString() {
        if (index >= end || input.getByte(index++) != '"')
            return fail(MarketDataParseStatus.MALFORMED);
        int bytes = 0;
        while (index < end) {
            final int value = input.getByte(index++) & 0xff;
            if (++bytes > maximumTokenBytes) return fail(MarketDataParseStatus.TOKEN_TOO_LONG);
            if (value == '"') return true;
            if (value == '\\') {
                if (index >= end) return fail(MarketDataParseStatus.MALFORMED);
                final byte escaped = input.getByte(index++);
                if (escaped == 'u') {
                    for (int i = 0; i < 4; i++) {
                        if (index >= end
                                || Character.digit((char) input.getByte(index++), 16) < 0) {
                            return fail(MarketDataParseStatus.MALFORMED);
                        }
                    }
                }
            } else if (value < 0x20) {
                return fail(MarketDataParseStatus.MALFORMED);
            }
        }
        return fail(MarketDataParseStatus.MALFORMED);
    }

    private boolean parseScaled(
            final int offset, final int length, final int scale, final MutableJsonLong target) {
        return parseScaled(offset, length, scale, target, false);
    }

    private boolean parseScaled(
            final int offset,
            final int length,
            final int scale,
            final MutableJsonLong target,
            final boolean allowExponent) {
        if (length == 0 || scale < 0 || scale > 18)
            return fail(MarketDataParseStatus.INVALID_NUMBER);
        int cursor = offset;
        final int limit = offset + length;
        boolean negative = false;
        if (input.getByte(cursor) == '-' || input.getByte(cursor) == '+') {
            negative = input.getByte(cursor++) == '-';
            if (cursor == limit) return fail(MarketDataParseStatus.INVALID_NUMBER);
        }
        final long lowerLimit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        final long multiplyLimit = lowerLimit / 10;
        long accumulated = 0;
        int integerDigits = 0;
        int fractionalDigits = 0;
        boolean decimal = false;
        int exponent = 0;
        boolean exponentSeen = false;
        while (cursor < limit) {
            final int character = input.getByte(cursor++) & 0xff;
            if (character == 'e' || character == 'E') {
                if (!allowExponent
                        || exponentSeen
                        || integerDigits == 0
                        || (decimal && fractionalDigits == 0)
                        || cursor == limit) {
                    return fail(MarketDataParseStatus.INVALID_NUMBER);
                }
                exponentSeen = true;
                boolean negativeExponent = false;
                int next = input.getByte(cursor) & 0xff;
                if (next == '+' || next == '-') {
                    negativeExponent = next == '-';
                    if (++cursor == limit) return fail(MarketDataParseStatus.INVALID_NUMBER);
                }
                int exponentDigits = 0;
                while (cursor < limit) {
                    final int exponentDigit = (input.getByte(cursor++) & 0xff) - '0';
                    if (exponentDigit < 0 || exponentDigit > 9 || exponent > 100) {
                        return fail(MarketDataParseStatus.INVALID_NUMBER);
                    }
                    exponent = exponent * 10 + exponentDigit;
                    exponentDigits++;
                }
                if (exponentDigits == 0) return fail(MarketDataParseStatus.INVALID_NUMBER);
                if (negativeExponent) exponent = -exponent;
                break;
            }
            if (character == '.') {
                if (decimal || integerDigits == 0 || cursor == limit)
                    return fail(MarketDataParseStatus.INVALID_NUMBER);
                decimal = true;
                continue;
            }
            final int digit = character - '0';
            if (digit < 0 || digit > 9) return fail(MarketDataParseStatus.INVALID_NUMBER);
            if (decimal) {
                fractionalDigits++;
                if (!allowExponent && fractionalDigits > scale) {
                    return fail(MarketDataParseStatus.INVALID_NUMBER);
                }
            }
            if (!decimal) integerDigits++;
            if (accumulated < multiplyLimit) return fail(MarketDataParseStatus.INVALID_NUMBER);
            accumulated *= 10;
            if (accumulated < lowerLimit + digit) return fail(MarketDataParseStatus.INVALID_NUMBER);
            accumulated -= digit;
        }
        final int power = scale + exponent - fractionalDigits;
        if (power > 18 || power < -18) return fail(MarketDataParseStatus.INVALID_NUMBER);
        if (power >= 0) {
            for (int i = 0; i < power; i++) {
                if (accumulated < multiplyLimit) return fail(MarketDataParseStatus.INVALID_NUMBER);
                accumulated *= 10;
            }
        } else {
            for (int i = 0; i > power; i--) {
                if (accumulated % 10 != 0) return fail(MarketDataParseStatus.INVALID_NUMBER);
                accumulated /= 10;
            }
        }
        target.value(negative ? accumulated : -accumulated);
        return true;
    }

    private void skipWhitespace() {
        while (index < end && isWhitespace(input.getByte(index))) index++;
    }

    private static boolean isWhitespace(final byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t';
    }

    private boolean fail(final MarketDataParseStatus failure) {
        status = failure;
        return false;
    }
}
