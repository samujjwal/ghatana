/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.av;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AVAsset model.
 * 
 * P8.1: Verify AV asset/entity model.
 * 
 * @doc.type test
 * @doc.purpose Verify AV asset model behavior
 * @doc.layer product
 */
@DisplayName("AVAsset Tests")
class AVAssetTest {

    @Test
    @DisplayName("Builder creates valid AVAsset with required fields")
    void builderCreatesValidAsset() {
        AVAsset asset = AVAsset.builder()
            .tenantId("tenant-1")
            .type(AVAsset.AVAssetType.VIDEO)
            .format(AVAsset.AVAssetFormat.MP4)
            .consent(new AVAsset.AVConsent(true, AVAsset.AVConsent.ConsentType.EXPLICIT, Instant.now(), "user", false, null))
            .retention(new AVAsset.AVRetention(365, true, 180, false, List.of()))
            .build();

        assertNotNull(asset.id());
        assertEquals("tenant-1", asset.tenantId());
        assertEquals(AVAsset.AVAssetType.VIDEO, asset.type());
        assertEquals(AVAsset.AVAssetFormat.MP4, asset.format());
        assertTrue(asset.consent().consented());
    }

    @Test
    @DisplayName("Builder throws for missing required fields")
    void builderThrowsForMissingFields() {
        assertThrows(IllegalStateException.class, () -> AVAsset.builder().build());
        assertThrows(IllegalStateException.class, () -> AVAsset.builder().tenantId("tenant-1").build());
        assertThrows(IllegalStateException.class, () -> AVAsset.builder().tenantId("tenant-1").type(AVAsset.AVAssetType.AUDIO).build());
    }

    @Test
    @DisplayName("AVTranscript record stores transcript data")
    void avTranscriptStoresData() {
        List<AVAsset.TranscriptSegment> segments = List.of(
            new AVAsset.TranscriptSegment(0, 1000, "Hello world", 0.95),
            new AVAsset.TranscriptSegment(1000, 2000, "Test transcript", 0.90)
        );
        
        AVAsset.AVTranscript transcript = new AVAsset.AVTranscript(
            "transcript-1", "en-US", segments, 0.92, "provider-1", Instant.now()
        );

        assertEquals("transcript-1", transcript.id());
        assertEquals("en-US", transcript.language());
        assertEquals(2, transcript.segments().size());
        assertEquals(0.92, transcript.confidence());
    }

    @Test
    @DisplayName("AVFrameIndex record stores frame index data")
    void avFrameIndexStoresData() {
        List<Long> keyFrames = List.of(0L, 1000L, 2000L, 3000L);
        
        AVAsset.AVFrameIndex frameIndex = new AVAsset.AVFrameIndex(
            "index-1", 3000, 30.0, keyFrames, Instant.now()
        );

        assertEquals("index-1", frameIndex.id());
        assertEquals(3000, frameIndex.frameCount());
        assertEquals(30.0, frameIndex.frameRate());
        assertEquals(4, frameIndex.keyFrames().size());
    }

    @Test
    @DisplayName("AVConsent record stores consent information")
    void avConsentStoresData() {
        AVAsset.AVConsent consent = new AVAsset.AVConsent(
            true, AVAsset.AVConsent.ConsentType.EXPLICIT, Instant.now(), "user", false, null
        );

        assertTrue(consent.consented());
        assertEquals(AVAsset.AVConsent.ConsentType.EXPLICIT, consent.consentType());
        assertFalse(consent.legalHold());
    }

    @Test
    @DisplayName("AVRetention record stores retention policy")
    void avRetentionStoresData() {
        AVAsset.AVRetention retention = new AVAsset.AVRetention(
            365, true, 180, true, List.of("rule-1", "rule-2")
        );

        assertEquals(365, retention.retentionPeriod());
        assertTrue(retention.deleteAfter());
        assertEquals(180, retention.archiveAfter());
        assertTrue(retention.redactionRequired());
        assertEquals(2, retention.redactionRules().size());
    }

    @Test
    @DisplayName("toBuilder creates copy with same values")
    void toBuilderCreatesCopy() {
        AVAsset original = AVAsset.builder()
            .tenantId("tenant-1")
            .type(AVAsset.AVAssetType.AUDIO)
            .format(AVAsset.AVAssetFormat.MP3)
            .consent(new AVAsset.AVConsent(true, AVAsset.AVConsent.ConsentType.EXPLICIT, Instant.now(), "user", false, null))
            .retention(new AVAsset.AVRetention(365, true, null, false, List.of()))
            .tag("custom", "value")
            .build();

        AVAsset copy = original.toBuilder().build();

        assertEquals(original.id(), copy.id());
        assertEquals(original.tenantId(), copy.tenantId());
        assertEquals(original.type(), copy.type());
        assertEquals(original.tags(), copy.tags());
    }

    @Test
    @DisplayName("Optional fields return empty when not set")
    void optionalFieldsReturnEmpty() {
        AVAsset asset = AVAsset.builder()
            .tenantId("tenant-1")
            .type(AVAsset.AVAssetType.AUDIO)
            .format(AVAsset.AVAssetFormat.MP3)
            .consent(new AVAsset.AVConsent(true, AVAsset.AVConsent.ConsentType.EXPLICIT, Instant.now(), "user", false, null))
            .retention(new AVAsset.AVRetention(365, true, null, false, List.of()))
            .build();

        assertTrue(asset.transcript().isEmpty());
        assertTrue(asset.frameIndex().isEmpty());
    }
}
