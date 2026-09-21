package com.penguinsecure.basis.strategy.api.pricing;

import com.penguinsecure.basis.core.numeric.DecimalScale;

/** Fixed startup table for liquidity haircuts and latency reserves. */
public final class LiquidityRiskTable {
    private final int haircutModelId;
    private final int latencyRiskModelId;
    private final int rateScale;
    private final InputMetadata metadata;
    private final int[] directions;
    private final int[] firstVenues;
    private final int[] secondVenues;
    private final long[] maximumExposures;
    private final int[] volatilityRegimes;
    private final long[] haircutRates;
    private final long[] latencyRiskRates;
    private int size;
    private boolean frozen;

    public LiquidityRiskTable(
            final int haircutModelId,
            final int latencyRiskModelId,
            final int rateScale,
            final InputMetadata metadata,
            final int capacity) {
        if (haircutModelId <= 0 || latencyRiskModelId <= 0 || capacity <= 0) {
            throw new IllegalArgumentException("invalid table identity or capacity");
        }
        new DecimalScale(rateScale);
        if (metadata == null) throw new NullPointerException("metadata is required");
        this.haircutModelId = haircutModelId;
        this.latencyRiskModelId = latencyRiskModelId;
        this.rateScale = rateScale;
        this.metadata = metadata;
        directions = new int[capacity];
        firstVenues = new int[capacity];
        secondVenues = new int[capacity];
        maximumExposures = new long[capacity];
        volatilityRegimes = new int[capacity];
        haircutRates = new long[capacity];
        latencyRiskRates = new long[capacity];
    }

    public LiquidityRiskTable add(
            final BasisDirection direction,
            final int firstVenue,
            final int secondVenue,
            final long maximumExposure,
            final int volatilityRegime,
            final long haircutRate,
            final long latencyRiskRate) {
        if (frozen) throw new IllegalStateException("table is frozen");
        if (direction == null
                || firstVenue <= 0
                || secondVenue <= 0
                || maximumExposure <= 0
                || volatilityRegime < 0
                || haircutRate < 0
                || latencyRiskRate < 0
                || haircutRate
                        >= com.penguinsecure.basis.core.numeric.CheckedDecimalMath.powerOfTen(
                                rateScale)) {
            throw new IllegalArgumentException("invalid liquidity-risk row");
        }
        if (size == directions.length) throw new IllegalStateException("table capacity exceeded");
        for (int index = 0; index < size; index++) {
            if (directions[index] == direction.code()
                    && firstVenues[index] == firstVenue
                    && secondVenues[index] == secondVenue
                    && maximumExposures[index] == maximumExposure
                    && volatilityRegimes[index] == volatilityRegime) {
                throw new IllegalStateException("duplicate liquidity-risk row");
            }
        }
        directions[size] = direction.code();
        firstVenues[size] = firstVenue;
        secondVenues[size] = secondVenue;
        maximumExposures[size] = maximumExposure;
        volatilityRegimes[size] = volatilityRegime;
        haircutRates[size] = haircutRate;
        latencyRiskRates[size] = latencyRiskRate;
        size++;
        return this;
    }

    public LiquidityRiskTable freeze() {
        if (size == 0) throw new IllegalStateException("empty liquidity-risk table");
        frozen = true;
        return this;
    }

    public int find(
            final BasisDirection direction,
            final int firstVenue,
            final int secondVenue,
            final long exposure,
            final int volatilityRegime) {
        if (!frozen || direction == null || exposure <= 0) return -1;
        int selected = -1;
        long selectedMaximum = Long.MAX_VALUE;
        for (int index = 0; index < size; index++) {
            if (directions[index] == direction.code()
                    && firstVenues[index] == firstVenue
                    && secondVenues[index] == secondVenue
                    && volatilityRegimes[index] == volatilityRegime
                    && exposure <= maximumExposures[index]
                    && maximumExposures[index] < selectedMaximum) {
                selected = index;
                selectedMaximum = maximumExposures[index];
            }
        }
        return selected;
    }

    public int haircutModelId() {
        return haircutModelId;
    }

    public int latencyRiskModelId() {
        return latencyRiskModelId;
    }

    public int rateScale() {
        return rateScale;
    }

    public InputMetadata metadata() {
        return metadata;
    }

    public long haircutRate(final int index) {
        return haircutRates[index];
    }

    public long latencyRiskRate(final int index) {
        return latencyRiskRates[index];
    }
}
