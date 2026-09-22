package com.penguinsecure.basis.app.marketdata;

record PublicMarketDataOptions(VenueSelection venues, int durationSeconds, int reportSeconds) {
    private static final int MAXIMUM_DURATION_SECONDS = 86_400;

    PublicMarketDataOptions {
        if (venues == null) throw new NullPointerException("venues are required");
        if (durationSeconds < 0 || durationSeconds > MAXIMUM_DURATION_SECONDS) {
            throw new IllegalArgumentException(
                    "duration-seconds must be between 0 and " + MAXIMUM_DURATION_SECONDS);
        }
        if (reportSeconds <= 0 || reportSeconds > 3_600) {
            throw new IllegalArgumentException("report-seconds must be between 1 and 3600");
        }
    }

    static PublicMarketDataOptions parse(final String[] arguments) {
        VenueSelection venues = VenueSelection.BOTH;
        int durationSeconds = 60;
        int reportSeconds = 5;
        for (String argument : arguments) {
            if (argument.startsWith("--venue=")) {
                venues = VenueSelection.parse(argument.substring("--venue=".length()));
            } else if (argument.startsWith("--duration-seconds=")) {
                durationSeconds =
                        parseInteger(
                                "duration-seconds",
                                argument.substring("--duration-seconds=".length()));
            } else if (argument.startsWith("--report-seconds=")) {
                reportSeconds =
                        parseInteger(
                                "report-seconds", argument.substring("--report-seconds=".length()));
            } else {
                throw new IllegalArgumentException("unknown argument: " + argument);
            }
        }
        return new PublicMarketDataOptions(venues, durationSeconds, reportSeconds);
    }

    private static int parseInteger(final String name, final String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    enum VenueSelection {
        BYBIT,
        DERIBIT,
        BOTH;

        boolean includesBybit() {
            return this == BYBIT || this == BOTH;
        }

        boolean includesDeribit() {
            return this == DERIBIT || this == BOTH;
        }

        static VenueSelection parse(final String value) {
            return switch (value) {
                case "bybit" -> BYBIT;
                case "deribit" -> DERIBIT;
                case "both" -> BOTH;
                default ->
                        throw new IllegalArgumentException("venue must be bybit, deribit, or both");
            };
        }
    }
}
