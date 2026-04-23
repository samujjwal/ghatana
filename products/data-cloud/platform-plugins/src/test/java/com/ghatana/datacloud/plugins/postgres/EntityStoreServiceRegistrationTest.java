package com.ghatana.datacloud.plugins.postgres;

import com.ghatana.datacloud.spi.EntityStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityStore Service Registration Tests")
class EntityStoreServiceRegistrationTest {

    @Test
    @DisplayName("platform plugins expose the PostgreSQL EntityStore via ServiceLoader")
    void platformPluginsExposeThePostgresEntityStoreViaServiceLoader() { // GH-90000
        assertThat(ServiceLoader.load(EntityStore.class).findFirst()) // GH-90000
            .isPresent() // GH-90000
            .get() // GH-90000
            .isInstanceOf(PostgresEntityStore.class); // GH-90000
    }
}