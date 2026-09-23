package com.penguinsecure.basis.app.assembly;

import com.penguinsecure.basis.app.lifecycle.StartupEvidence;

/** Ordered cold-start effects implemented by the concrete process adapters. */
public interface StartupPort {
    boolean loadConfiguration();

    boolean startArchive();

    boolean loadVenueMetadata();

    boolean reconcilePrivateState();

    boolean synchronizeMarketData();

    boolean warmUp();

    StartupEvidence evidence();
}
