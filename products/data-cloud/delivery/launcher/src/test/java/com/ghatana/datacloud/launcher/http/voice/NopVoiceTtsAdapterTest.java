/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.voice;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for NopVoiceTtsAdapter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("NopVoiceTtsAdapter")
class NopVoiceTtsAdapterTest {

    @Test
    @DisplayName("INSTANCE returns singleton")
    void instance_returnsSingleton() {
        assertThat(NopVoiceTtsAdapter.INSTANCE).isNotNull();
        assertThat(NopVoiceTtsAdapter.INSTANCE).isSameAs(NopVoiceTtsAdapter.INSTANCE);
    }

    @Test
    @DisplayName("synthesize returns empty byte array")
    void synthesize_returnsEmptyByteArray() {
        Promise<byte[]> result = NopVoiceTtsAdapter.INSTANCE.synthesize("Hello", "en-US");
        
        assertThat(result).isNotNull();
        byte[] audio = result.getResult();
        assertThat(audio).isNotNull();
        assertThat(audio).isEmpty();
    }

    @Test
    @DisplayName("isAvailable returns false")
    void isAvailable_returnsFalse() {
        assertThat(NopVoiceTtsAdapter.INSTANCE.isAvailable()).isFalse();
    }
}
