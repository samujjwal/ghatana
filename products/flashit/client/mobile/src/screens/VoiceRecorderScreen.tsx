import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Platform,
} from 'react-native';
import {
  useAudioRecorder,
  useAudioRecorderState,
  RecordingPresets,
  requestRecordingPermissionsAsync,
  setAudioModeAsync
} from 'expo-audio';
import { Audio } from 'expo-av';
import * as FileSystem from 'expo-file-system/legacy';
import { useNavigation } from '@react-navigation/native';
import { WaveformVisualizer } from '../components/WaveformVisualizer';
import { AudioPlaybackControls } from '../components/AudioPlaybackControls';

const MAX_RECORDING_DURATION_MS = 5 * 60 * 1000; // 5 minutes

interface RecordingState {
  uri: string | null;
  duration: number;
  isRecording: boolean;
  isPaused: boolean;
}

/**
 * Voice Recorder Screen for mobile audio capture
 * 
 * @doc.type screen
 * @doc.purpose Provide voice recording with waveform visualization and playback
 * @doc.layer product
 * @doc.pattern Screen
 */
export const VoiceRecorderScreen: React.FC = () => {
  const navigation = useNavigation();

  const handleGoBack = () => {
    try {
      if (navigation && typeof navigation.goBack === 'function') {
        navigation.goBack();
      }
    } catch (error) {
      console.error('Navigation error:', error);
    }
  };
  const [recordingState, setRecordingState] = useState<RecordingState>({
    uri: null,
    duration: 0,
    isRecording: false,
    isPaused: false,
  });
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [audioLevels, setAudioLevels] = useState<number[]>([]);
  const [sound, setSound] = useState<Audio.Sound | null>(null);

  // Create recorder instance
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder);

  useEffect(() => {
    requestPermissions();
    return () => {
      // Cleanup on unmount
      if (recorder) {
        recorder.stop();
      }
      if (sound) {
        sound.unloadAsync();
      }
    };
  }, []);

  // Update recording state based on recorder state
  useEffect(() => {
    if (recorderState) {
      setRecordingState(prev => ({
        ...prev,
        isRecording: recorderState.isRecording,
        duration: Math.floor(recorderState.durationMillis || 0),
        uri: recorderState.url || null,
      }));
    }
  }, [recorderState]);

  const requestPermissions = async () => {
    try {
      const { status, granted } = await requestRecordingPermissionsAsync();
      setPermissionGranted(granted);

      if (!granted) {
        Alert.alert(
          'Permission Required',
          'Microphone permission is required to record audio.',
          [{ text: 'OK', onPress: handleGoBack }]
        );
      }
    } catch (error) {
      console.error('Error requesting permissions:', error);
      Alert.alert('Error', 'Failed to request microphone permission');
    }
  };

  const startRecording = async () => {
    if (!permissionGranted || !recorder) {
      Alert.alert('Permission Required', 'Microphone permission is required to record audio.');
      return;
    }

    try {
      recorder.record();
      setRecordingState(prev => ({
        ...prev,
        duration: 0,
        isRecording: true,
        isPaused: false,
      }));
      setAudioLevels([]);
    } catch (error) {
      console.error('Failed to start recording:', error);
      Alert.alert('Error', 'Failed to start recording. Please try again.');
    }
  };

  const pauseRecording = async () => {
    if (!recorder || !recorderState.isRecording) return;

    try {
      recorder.pause();
      setRecordingState(prev => ({ ...prev, isPaused: true }));
    } catch (error) {
      console.error('Failed to pause recording:', error);
    }
  };

  const resumeRecording = async () => {
    if (!recorder || !recorderState.isRecording) return;

    try {
      recorder.record();
      setRecordingState(prev => ({ ...prev, isPaused: false }));
    } catch (error) {
      console.error('Failed to resume recording:', error);
    }
  };

  const stopRecording = async () => {
    if (!recorder) return;

    try {
      await recorder.stop();
      const uri = recorderState.url;

      setRecordingState(prev => ({
        ...prev,
        isRecording: false,
        isPaused: false,
        uri,
      }));

      await setAudioModeAsync({
        allowsRecording: true,
        playsInSilentMode: true,
        shouldPlayInBackground: false,
      });
    } catch (error) {
      console.error('Failed to stop recording:', error);
      Alert.alert('Error', 'Failed to stop recording');
    }
  };

  const deleteRecording = () => {
    Alert.alert(
      'Delete Recording',
      'Are you sure you want to delete this recording?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => {
            if (sound) {
              sound.unloadAsync();
              setSound(null);
            }
            setRecordingState({
              uri: null,
              duration: 0,
              isRecording: false,
              isPaused: false,
            });
            setAudioLevels([]);
          },
        },
      ]
    );
  };

  const saveRecording = async () => {
    if (!recordingState.uri) return;

    try {
      // Generate unique filename
      const timestamp = new Date().getTime();
      const filename = `voice_${timestamp}.m4a`;
      const destUri = `${FileSystem.documentDirectory}${filename}`;

      // Copy to permanent storage
      await FileSystem.copyAsync({
        from: recordingState.uri,
        to: destUri,
      });

      Alert.alert(
        'Recording Saved',
        'Your voice recording has been saved successfully.',
        [
          {
            text: 'OK',
            onPress: () => {
              // Navigate to moment creation with audio attachment
              // @ts-ignore - navigation type
              navigation.navigate('Capture', { audioUri: destUri });
            },
          },
        ]
      );
    } catch (error) {
      console.error('Failed to save recording:', error);
      Alert.alert('Error', 'Failed to save recording');
    }
  };

  const formatDuration = (ms: number): string => {
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor((ms / (1000 * 60)) % 60);
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const getRemainingTime = (): string => {
    const remaining = MAX_RECORDING_DURATION_MS - recordingState.duration;
    return formatDuration(remaining);
  };

  if (!permissionGranted) {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionTitle}>Microphone Access Required</Text>
          <Text style={styles.permissionText}>
            To record voice notes, please grant microphone permission.
          </Text>
          <TouchableOpacity 
            style={styles.permissionButton} 
            onPress={requestPermissions}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Grant microphone permission"
          >
            <Text style={styles.permissionButtonText}>Grant Permission</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity 
          onPress={handleGoBack}
          accessible={true}
          accessibilityRole="button"
          accessibilityLabel="Cancel recording"
        >
          <Text style={styles.headerButton}>Cancel</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Voice Recorder</Text>
        <View style={styles.headerButton} />
      </View>

      {/* Timer and Status */}
      <View style={styles.timerContainer}>
        <Text style={styles.timerText}>{formatDuration(recordingState.duration)}</Text>
        {recordingState.isRecording && (
          <Text style={styles.remainingText}>
            {getRemainingTime()} remaining
          </Text>
        )}
        {recordingState.isPaused && (
          <Text style={styles.statusText}>Paused</Text>
        )}
      </View>

      {/* Waveform Visualization */}
      <View style={styles.waveformContainer}>
        {recordingState.isRecording ? (
          <WaveformVisualizer
            audioLevels={audioLevels}
            isRecording={recordingState.isRecording && !recordingState.isPaused}
          />
        ) : recordingState.uri ? (
          <AudioPlaybackControls
            audioUri={recordingState.uri}
            duration={recordingState.duration}
            onSoundLoaded={setSound}
          />
        ) : (
          <View style={styles.emptyWaveform}>
            <Text style={styles.emptyText}>Tap to start recording</Text>
          </View>
        )}
      </View>

      {/* Recording Controls */}
      <View style={styles.controlsContainer}>
        {!recordingState.isRecording && !recordingState.uri && (
          <TouchableOpacity
            style={[styles.recordButton, styles.recordButtonActive]}
            onPress={startRecording}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Start recording"
          >
            <View style={styles.recordButtonInner} />
          </TouchableOpacity>
        )}

        {recordingState.isRecording && (
          <View style={styles.recordingControls}>
            <TouchableOpacity
              style={styles.controlButton}
              onPress={recordingState.isPaused ? resumeRecording : pauseRecording}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel={recordingState.isPaused ? "Resume recording" : "Pause recording"}
            >
              <Text style={styles.controlButtonText}>
                {recordingState.isPaused ? '▶' : '⏸'}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.recordButton, styles.recordButtonStop]}
              onPress={stopRecording}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Stop recording"
            >
              <View style={styles.stopButtonInner} />
            </TouchableOpacity>
          </View>
        )}

        {recordingState.uri && !recordingState.isRecording && (
          <View style={styles.finishedControls}>
            <TouchableOpacity 
              style={styles.deleteButton} 
              onPress={deleteRecording}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Delete recording"
            >
              <Text style={styles.deleteButtonText}>Delete</Text>
            </TouchableOpacity>
            <TouchableOpacity 
              style={styles.saveButton} 
              onPress={saveRecording}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Save and use recording"
            >
              <Text style={styles.saveButtonText}>Save & Use</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Quality Indicator */}
      <View style={styles.footer}>
        <Text style={styles.footerText}>High Quality • 64kbps</Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: Platform.OS === 'ios' ? 50 : 20,
    paddingHorizontal: 20,
    paddingBottom: 20,
  },
  headerButton: {
    width: 60,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#fff',
  },
  timerContainer: {
    alignItems: 'center',
    paddingVertical: 30,
  },
  timerText: {
    fontSize: 48,
    fontWeight: '700',
    color: '#fff',
    fontVariant: ['tabular-nums'],
  },
  remainingText: {
    fontSize: 14,
    color: '#888',
    marginTop: 8,
  },
  statusText: {
    fontSize: 16,
    color: '#ff9500',
    marginTop: 8,
    fontWeight: '600',
  },
  waveformContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 20,
  },
  emptyWaveform: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: '#666',
  },
  controlsContainer: {
    paddingVertical: 40,
    paddingHorizontal: 20,
    alignItems: 'center',
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
  finishedControls: {
    flexDirection: 'row',
    gap: 20,
  },
  deleteButton: {
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 12,
    backgroundColor: '#333',
  },
  deleteButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#ff3b30',
  },
  saveButton: {
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 12,
    backgroundColor: '#007aff',
  },
  saveButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  footer: {
    paddingBottom: 20,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 12,
    color: '#666',
  },
  permissionContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  permissionTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: '#fff',
    marginBottom: 16,
  },
  permissionText: {
    fontSize: 16,
    color: '#888',
    textAlign: 'center',
    marginBottom: 32,
  },
  permissionButton: {
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 12,
    backgroundColor: '#007aff',
  },
  permissionButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
});
