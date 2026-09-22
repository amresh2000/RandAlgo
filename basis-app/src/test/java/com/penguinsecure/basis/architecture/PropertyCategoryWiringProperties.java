package com.penguinsecure.basis.architecture;

import net.jqwik.api.Property;
import net.jqwik.api.Tag;

@Tag("property")
final class PropertyCategoryWiringProperties {
    @Property(tries = 1)
    void propertyCategoryIsDiscoverable() {}
}
