package com.ghatana.aep.launcher;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.event.EventLogStoreBackedEventCloud;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AepEventCloudResolutionTest {

    @Test
    void createResolvesEventLogStoreBackedEventCloudWhenProviderPresent() {
        AepEngine engine = Aep.create(Aep.AepConfig.defaults());
        try {
            assertInstanceOf(EventLogStoreBackedEventCloud.class, engine.eventCloud());
        } finally {
            engine.close();
        }
    }
}
