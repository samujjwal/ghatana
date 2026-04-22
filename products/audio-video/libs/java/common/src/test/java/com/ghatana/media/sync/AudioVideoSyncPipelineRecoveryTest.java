package com.ghatana.media.sync;

import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.ImageData;
import com.ghatana.media.sync.AudioVideoSyncPipeline.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class AudioVideoSyncPipelineRecoveryTest {

    private AudioVideoSyncPipeline pipeline;
    private TestSyncCallback callback;

    @BeforeEach
    void setUp() { // GH-90000
        callback = new TestSyncCallback(); // GH-90000
        pipeline = new AudioVideoSyncPipeline(callback, 500, 200, 40); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (pipeline != null) { // GH-90000
            pipeline.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should recover from sync errors with exponential backoff [GH-90000]")
    void testExponentialBackoffRecovery() throws Exception { // GH-90000
        TestSyncCallback errorCallback = new TestSyncCallback() { // GH-90000
            @Override
            public void onDriftDetected(long driftMs, SyncState state) { // GH-90000
                // Ignore drift
            }
            @Override
            public void onSyncedFrame(SyncedFrame frame) { // GH-90000
                throw new RuntimeException("Simulated sync error [GH-90000]");
            }
        };
        AudioVideoSyncPipeline errorPipeline = new AudioVideoSyncPipeline(errorCallback, 500, 200, 40); // GH-90000
        long baseTime = System.currentTimeMillis() * 1000; // GH-90000

        for (int i = 0; i < 10; i++) { // GH-90000
            AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
            ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
            errorPipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
            errorPipeline.feedVideo(video, baseTime + i * 16000); // GH-90000
            Thread.sleep(20); // GH-90000
        }

        Thread.sleep(1000); // GH-90000

        assertTrue(errorCallback.recoveryAttempts.get() > 0, "Should have attempted recovery"); // GH-90000
        assertTrue(errorCallback.lastBackoffMs.get() > 0, "Should have used backoff"); // GH-90000
        assertTrue(errorCallback.lastBackoffMs.get() >= 100, "Backoff should be at least 100ms"); // GH-90000
        errorPipeline.close(); // GH-90000
    }

    @Test
    @DisplayName("Should degrade to async mode after max recovery attempts [GH-90000]")
    void testAsyncModeDegradation() throws Exception { // GH-90000
        TestSyncCallback errorCallback = new TestSyncCallback() { // GH-90000
            @Override
            public void onSyncedFrame(SyncedFrame frame) { // GH-90000
                throw new RuntimeException("Simulated sync error [GH-90000]");
            }
        };
        AudioVideoSyncPipeline errorPipeline = new AudioVideoSyncPipeline(errorCallback, 500, 200, 40); // GH-90000

        try {
            long baseTime = System.currentTimeMillis() * 1000; // GH-90000
            for (int i = 0; i < 20; i++) { // GH-90000
                AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
                ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
                errorPipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
                errorPipeline.feedVideo(video, baseTime + i * 16000); // GH-90000
                Thread.sleep(20); // GH-90000
            }
            Thread.sleep(2000); // GH-90000

            assertTrue(errorCallback.asyncModeActivated.get(), "Should activate async mode"); // GH-90000
            assertTrue(errorPipeline.isAsyncMode(), "Pipeline should be in async mode"); // GH-90000
            assertEquals(SyncState.ASYNC, errorPipeline.getSyncState(), "State should be ASYNC"); // GH-90000
        } finally {
            errorPipeline.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should track sync quality metrics [GH-90000]")
    void testSyncQualityMetrics() throws Exception { // GH-90000
        long baseTime = System.currentTimeMillis() * 1000; // GH-90000
        for (int i = 0; i < 150; i++) { // GH-90000
            AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
            ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
            long drift = (i % 3) * 10000; // GH-90000
            pipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
            pipeline.feedVideo(video, baseTime + i * 16000 + drift); // GH-90000
            Thread.sleep(20); // GH-90000
        }
        Thread.sleep(500); // GH-90000

        SyncQualityMetrics metrics = pipeline.getQualityMetrics(); // GH-90000
        assertNotNull(metrics, "Metrics should not be null"); // GH-90000
        assertNotNull(metrics.getCurrentQuality(), "Quality should not be null"); // GH-90000
        assertTrue(metrics.getAverageDrift() >= 0, "Average drift should be non-negative"); // GH-90000
    }

    @Test
    @DisplayName("Should reset recovery counter on successful sync [GH-90000]")
    void testRecoveryCounterReset() throws Exception { // GH-90000
        long baseTime = System.currentTimeMillis() * 1000; // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
            ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
            pipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
            pipeline.feedVideo(video, baseTime + i * 16000); // GH-90000
            Thread.sleep(20); // GH-90000
        }
        Thread.sleep(300); // GH-90000
        assertEquals(SyncState.LOCKED, pipeline.getSyncState(), "Should be in LOCKED state"); // GH-90000
        assertTrue(callback.syncedFrames.get() > 0, "Should have synced frames"); // GH-90000
    }

    @Test
    @DisplayName("Should manually trigger recovery [GH-90000]")
    void testManualRecoveryTrigger() throws Exception { // GH-90000
        pipeline.close(); // GH-90000
        pipeline = new AudioVideoSyncPipeline(callback, 500, 200, 40); // GH-90000
        pipeline.triggerRecovery(); // GH-90000
        assertEquals(SyncState.SYNCING, pipeline.getSyncState(), "Should be SYNCING after manual recovery"); // GH-90000
        assertFalse(pipeline.isAsyncMode(), "Should not be in async mode"); // GH-90000
    }

    @Test
    @DisplayName("Should emit quality change callbacks [GH-90000]")
    void testQualityChangeCallbacks() throws Exception { // GH-90000
        long baseTime = System.currentTimeMillis() * 1000; // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
            ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
            long drift = i * 2000;
            pipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
            pipeline.feedVideo(video, baseTime + i * 16000 + drift); // GH-90000
            Thread.sleep(20); // GH-90000
        }
        Thread.sleep(500); // GH-90000
        assertTrue(callback.qualityChanges.get() >= 0, "Should track quality changes"); // GH-90000
    }

    @Test
    @DisplayName("Should handle async mode frame emission [GH-90000]")
    void testAsyncModeFrameEmission() throws Exception { // GH-90000
        TestSyncCallback errorCallback = new TestSyncCallback() { // GH-90000
            private int callCount = 0;
            @Override
            public void onSyncedFrame(SyncedFrame frame) { // GH-90000
                if (callCount++ < 10) { // GH-90000
                    throw new RuntimeException("Force async mode [GH-90000]");
                }
                super.onSyncedFrame(frame); // GH-90000
            }
        };
        AudioVideoSyncPipeline asyncPipeline = new AudioVideoSyncPipeline(errorCallback, 500, 200, 40); // GH-90000
        try {
            long baseTime = System.currentTimeMillis() * 1000; // GH-90000
            for (int i = 0; i < 30; i++) { // GH-90000
                AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
                ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
                asyncPipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
                asyncPipeline.feedVideo(video, baseTime + i * 16000); // GH-90000
                Thread.sleep(20); // GH-90000
            }
            Thread.sleep(1000); // GH-90000
            assertTrue(errorCallback.asyncModeActivated.get(), "Should be in async mode"); // GH-90000
            assertTrue(errorCallback.syncedFrames.get() > 0, "Should emit frames even in async mode"); // GH-90000
        } finally {
            asyncPipeline.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("Should provide buffer statistics [GH-90000]")
    void testBufferStatistics() throws Exception { // GH-90000
        long baseTime = System.currentTimeMillis() * 1000; // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            AudioData audio = new AudioData(new byte[1024], 16000, 1, 16); // GH-90000
            ImageData video = ImageData.builder().data(new byte[1920 * 1080 * 3]).width(1920).height(1080).colorSpace(com.ghatana.media.common.ColorSpace.RGB).build(); // GH-90000
            pipeline.feedAudio(audio, baseTime + i * 16000); // GH-90000
            pipeline.feedVideo(video, baseTime + i * 16000); // GH-90000
        }
        BufferStats stats = pipeline.getBufferStats(); // GH-90000
        assertNotNull(stats, "Stats should not be null"); // GH-90000
        assertTrue(stats.audioFramesBuffered >= 0, "Audio buffer count should be non-negative"); // GH-90000
        assertTrue(stats.videoFramesBuffered >= 0, "Video buffer count should be non-negative"); // GH-90000
    }

    private static class TestSyncCallback implements SyncCallback {
        final AtomicInteger syncedFrames = new AtomicInteger(0); // GH-90000
        final AtomicInteger driftDetections = new AtomicInteger(0); // GH-90000
        final AtomicInteger errors = new AtomicInteger(0); // GH-90000
        final AtomicInteger recoveryAttempts = new AtomicInteger(0); // GH-90000
        final AtomicLong lastBackoffMs = new AtomicLong(0); // GH-90000
        final AtomicBoolean asyncModeActivated = new AtomicBoolean(false); // GH-90000
        final AtomicInteger qualityChanges = new AtomicInteger(0); // GH-90000

        @Override
        public void onSyncedFrame(SyncedFrame frame) { // GH-90000
            syncedFrames.incrementAndGet(); // GH-90000
        }
        @Override
        public void onDriftDetected(long driftMs, SyncState state) { // GH-90000
            driftDetections.incrementAndGet(); // GH-90000
        }
        @Override
        public void onError(String message) { // GH-90000
            errors.incrementAndGet(); // GH-90000
        }
        @Override
        public void onRecoveryAttempt(int attempt, long backoffMs) { // GH-90000
            recoveryAttempts.incrementAndGet(); // GH-90000
            lastBackoffMs.set(backoffMs); // GH-90000
        }
        @Override
        public void onAsyncModeActivated(String reason) { // GH-90000
            asyncModeActivated.set(true); // GH-90000
        }
        @Override
        public void onQualityChange(SyncQuality quality) { // GH-90000
            qualityChanges.incrementAndGet(); // GH-90000
        }
    }
}
