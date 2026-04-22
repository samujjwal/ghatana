package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the main-classpath EventLogStore SPI registration is absent. */
@DisplayName("EventLogStore Service Registration Tests [GH-90000]")
class EventLogStoreServiceRegistrationTest {

    @Test
    @DisplayName("main classpath does not expose an EventLogStore service registration [GH-90000]")
    void mainClasspathDoesNotExposeAnEventLogStoreServiceRegistration() { // GH-90000
        assertThat(ServiceLoader.load(EventLogStore.class).findFirst()).isEmpty(); // GH-90000
    }
}
