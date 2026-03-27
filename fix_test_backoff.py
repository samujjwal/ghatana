import re

with open('platform/java/audio-video/src/test/java/com/ghatana/media/sync/AudioVideoSyncPipelineRecoveryTest.java', 'r') as f:
    content = f.read()

correct_code = """    @DisplayName("Should recover from sync errors with exponential backoff")
    void testExponentialBackoffRecovery() throws Exception {
        TestSyncCallback errorCallback = new TestSyncCallback() {
            @Override
            public void onDriftDetected(long driftMs, SyncState state) {
                throw new RuntimeException("Simulated sync error");
            }
            @Override
            public void onSyncedFrame(SyncedFrame frame) {
                throw new RuntimeException("Simulated sync error");
            }
        };
        AudioVideoSyncPipeline errorPipeline = new AudioVideoSyncPipeline(errorCallback, 500, 200, 40);
        long baseTime = System.currentTimeMillis() * 1000;"""

# Replace everything from the annotation up to the "Feed audio and video" comment
content = re.sub(r'    @DisplayName\("Should recover from sync errors with exponential backoff"\)\n    void testExponentialBackoffRecovery\(\) throws Exception \{.*?(?=        // Feed audio and video with large drift to trigger errors)', correct_code + '\n        \n', content, flags=re.DOTALL)

with open('platform/java/audio-video/src/test/java/com/ghatana/media/sync/AudioVideoSyncPipelineRecoveryTest.java', 'w') as f:
    f.write(content)
