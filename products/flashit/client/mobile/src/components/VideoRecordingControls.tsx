import React from 'react';
import { View, StyleSheet, TouchableOpacity, Text } from 'react-native';

interface VideoRecordingControlsProps {
  isRecording: boolean;
  isPaused: boolean;
  onStart: () => void;
  onPause: () => void;
  onResume: () => void;
  onStop: () => void;
}

/**
 * Video Recording Controls Component
 * 
 * @doc.type component
 * @doc.purpose Recording controls for video capture
 * @doc.layer product
 * @doc.pattern Component
 */
export const VideoRecordingControls: React.FC<VideoRecordingControlsProps> = ({
  isRecording,
  isPaused,
  onStart,
  onPause,
  onResume,
  onStop,
}) => {
  return (
    <View style={styles.container}>
      {!isRecording ? (
        // Start Recording Button
        <TouchableOpacity
          style={[styles.recordButton, styles.recordButtonActive]}
          onPress={onStart}
        >
          <View style={styles.recordButtonInner} />
        </TouchableOpacity>
      ) : (
        // Recording Controls
        <View style={styles.recordingControls}>
          <TouchableOpacity
            style={styles.controlButton}
            onPress={isPaused ? onResume : onPause}
          >
            <Text style={styles.controlButtonText}>
              {isPaused ? '▶' : '⏸'}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.recordButton, styles.recordButtonStop]}
            onPress={onStop}
          >
            <View style={styles.stopButtonInner} />
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    paddingVertical: 40,
    paddingHorizontal: 20,
    alignItems: 'center',
    backgroundColor: '#000',
  },
  recordButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  recordButtonActive: {
    backgroundColor: '#ff3b30',
  },
  recordButtonStop: {
    backgroundColor: '#ff3b30',
  },
  recordButtonInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#ff3b30',
  },
  stopButtonInner: {
    width: 30,
    height: 30,
    backgroundColor: '#fff',
    borderRadius: 4,
  },
  recordingControls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 30,
  },
  controlButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#333',
    justifyContent: 'center',
    alignItems: 'center',
  },
  controlButtonText: {
    fontSize: 24,
    color: '#fff',
  },
});
