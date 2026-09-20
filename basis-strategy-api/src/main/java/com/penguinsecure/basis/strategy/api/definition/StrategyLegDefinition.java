package com.penguinsecure.basis.strategy.api.definition;

/** Dense binding of one strategy leg to certified venue/account/feed state. */
public record StrategyLegDefinition(
        int venueId, int instrumentId, int accountId, int feedProfileId, long maximumAgeNanos) {}
