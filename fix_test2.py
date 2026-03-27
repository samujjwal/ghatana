import re

with open('platform/java/audio-video/src/test/java/com/ghatana/media/sync/AudioVideoSyncPipelineRecoveryTest.java', 'r') as f:
    content = f.read()

# Replace multiple @Override with a single @Override
content = re.sub(r'(\s*@Override)+', r'\1', content)
content = content.replace("            @Override\n            @Override", "            @Override")
content = content.replace("        @Override\n        @Override", "        @Override")

# Remove duplicate onDriftDetected in TestSyncCallback body if there are two
content = re.sub(r'(public void onDriftDetected\(long driftMs, SyncState state\) \{\s*throw new RuntimeException\("Simulated sync error"\);\s*\}.*?)public void onDriftDetected\(long driftMs, SyncState state\) \{', r'\1', content, flags=re.DOTALL)

with open('platform/java/audio-video/src/test/java/com/ghatana/media/sync/AudioVideoSyncPipelineRecoveryTest.java', 'w') as f:
    f.write(content)
