package com.penguinsecure.basis.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

final class TestCategoryWiringIT {
    @Test
    @Tag("integration")
    void integrationCategoryIsDiscoverable() {}

    @Test
    @Tag("venue-contract")
    void venueContractCategoryIsDiscoverable() {}

    @Test
    @Tag("replay")
    void replayCategoryIsDiscoverable() {}

    @Test
    @Tag("chaos")
    void chaosCategoryIsDiscoverable() {}
}
