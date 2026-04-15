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
    void platformPluginsExposeThePostgresEntityStoreViaServiceLoader() {
        assertThat(ServiceLoader.load(EntityStore.class).findFirst())
            .isPresent()
            .get()
            .isInstanceOf(PostgresEntityStore.class);
    }
}