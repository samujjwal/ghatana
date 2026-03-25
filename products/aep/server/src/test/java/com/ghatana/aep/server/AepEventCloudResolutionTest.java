package com.ghatana.aep.server;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.eventcloud.DataCloudBackedEventCloud;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AepEventCloudResolutionTest {

    @Test
    void createResolvesDataCloudBackedEventCloudWhenProviderPresent() {
        AepEngine engine = Aep.create(Aep.AepConfig.defaults());
        try {
            assertInstanceOf(DataCloudBackedEventCloud.class, engine.eventCloud());
        } finally {
            engine.close();
        }
    }
}
