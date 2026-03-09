import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Platform,
} from 'react-native';
import { CameraView, Camera, CameraType, VideoQuality } from 'expo-camera';
import * as FileSystem from 'expo-file-system/legacy';
import { useNavigation } from '@react-navigation/native';
import { VideoRecordingControls } from '../components/VideoRecordingControls';
import { VideoPreview } from '../components/VideoPreview';

const MAX_RECORDING_DURATION_MS = 2 * 60 * 1000; // 2 minutes

interface RecordingState {
  isRecording: boolean;
  isPaused: boolean;
  duration: number;
  videoUri: string | null;
}

/**
 * Video Recorder Screen for mobile video capture
 * 
 * @doc.type screen
 * @doc.purpose Provide video recording with 2-minute limit
 * @doc.layer product
 * @doc.pattern Screen
 */
export const VideoRecorderScreen: React.FC = () => {
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
  const cameraRef = useRef<any>(null);
  const [cameraPermission, setCameraPermission] = useState<boolean>(false);
  const [microphonePermission, setMicrophonePermission] = useState<boolean>(false);
  const [cameraType, setCameraType] = useState<CameraType>('back' as CameraType);
  const [recordingState, setRecordingState] = useState<RecordingState>({
    isRecording: false,
    isPaused: false,
    duration: 0,
    videoUri: null,
  });

  useEffect(() => {
    requestPermissions();
  }, []);

  useEffect(() => {
    // Timer for recording duration
    let interval: NodeJS.Timeout | null = null;
    if (recordingState.isRecording && !recordingState.isPaused) {
      interval = setInterval(() => {
        setRecordingState((prev) => {
          const newDuration = prev.duration + 1000;
          if (newDuration >= MAX_RECORDING_DURATION_MS) {
            stopRecording();
            return prev;
          }
          return { ...prev, duration: newDuration };
        });
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [recordingState.isRecording, recordingState.isPaused]);

  const requestPermissions = async () => {
    try {
      const cameraResult = await Camera.requestCameraPermissionsAsync();
      setCameraPermission(cameraResult.status === 'granted');

      const micResult = await Camera.requestMicrophonePermissionsAsync();
      setMicrophonePermission(micResult.status === 'granted');

      if (cameraResult.status !== 'granted' || micResult.status !== 'granted') {
        Alert.alert(
          'Permissions Required',
          'Camera and microphone permissions are required to record video.',
          [{ text: 'OK' }]
        );
      }
    } catch (error) {
      console.error('Error requesting permissions:', error);
      Alert.alert('Error', 'Failed to request permissions');
    }
  };

  const startRecording = async () => {
    if (!cameraRef.current || !cameraPermission || !microphonePermission) return;

    try {
      const video = await cameraRef.current.recordAsync({
        quality: '720p' as any,
        maxDuration: MAX_RECORDING_DURATION_MS / 1000, // Convert to seconds
        mute: false,
      });

      setRecordingState({
        isRecording: false,
        isPaused: false,
        duration: recordingState.duration,
        videoUri: video.uri,
      });
    } catch (error) {
      console.error('Error starting recording:', error);
      Alert.alert('Error', 'Failed to start recording');
      setRecordingState({
        isRecording: false,
        isPaused: false,
        duration: 0,
        videoUri: null,
      });
    }
  };

  const handleStartRecording = async () => {
    setRecordingState((prev) => ({
      ...prev,
      isRecording: true,
      isPaused: false,
      duration: 0,
    }));
    await startRecording();
  };

  const pauseRecording = async () => {
    if (!cameraRef.current) return;

    try {
      await cameraRef.current.pausePreview();
      setRecordingState((prev) => ({ ...prev, isPaused: true }));
    } catch (error) {
      console.error('Error pausing recording:', error);
    }
  };

  const resumeRecording = async () => {
    if (!cameraRef.current) return;

    try {
      await cameraRef.current.resumePreview();
      setRecordingState((prev) => ({ ...prev, isPaused: false }));
    } catch (error) {
      console.error('Error resuming recording:', error);
    }
  };

  const stopRecording = async () => {
    if (!cameraRef.current) return;

    try {
      await cameraRef.current.stopRecording();
    } catch (error) {
      console.error('Error stopping recording:', error);
    }
  };

  const deleteRecording = () => {
    Alert.alert(
      'Delete Recording',
      'Are you sure you want to delete this video?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => {
            setRecordingState({
              isRecording: false,
              isPaused: false,
              duration: 0,
              videoUri: null,
            });
          },
        },
      ]
    );
  };

  const saveRecording = async () => {
    if (!recordingState.videoUri) return;

    try {
      // Generate unique filename
      const timestamp = new Date().getTime();
      const filename = `video_${timestamp}.mp4`;
      const destUri = `${FileSystem.documentDirectory}${filename}`;

      // Copy to permanent storage
      await FileSystem.copyAsync({
        from: recordingState.videoUri,
        to: destUri,
      });

      // Get file size
      const fileInfo = await FileSystem.getInfoAsync(destUri);
      const fileSizeMB = fileInfo.size ? (fileInfo.size / (1024 * 1024)).toFixed(2) : '0';

      Alert.alert(
        'Video Saved',
        `Your video has been saved (${fileSizeMB} MB)`,
        [
          {
            text: 'OK',
            onPress: () => {
              // @ts-ignore - navigation type
              navigation.navigate('Capture', { videoUri: destUri });
            },
          },
        ]
      );
    } catch (error) {
      console.error('Error saving recording:', error);
      Alert.alert('Error', 'Failed to save video');
    }
  };

  const flipCamera = () => {
    setCameraType((current) =>
      current === 'back' ? 'front' as CameraType : 'back' as CameraType
    );
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

  if (!cameraPermission || !microphonePermission) {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionTitle}>Permissions Required</Text>
          <Text style={styles.permissionText}>
            Camera and microphone permissions are required to record video.
          </Text>
          <TouchableOpacity 
            style={styles.permissionButton} 
            onPress={requestPermissions}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Grant camera and microphone permissions"
          >
            <Text style={styles.permissionButtonText}>Grant Permissions</Text>
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
        <Text style={styles.headerTitle}>Video Recorder</Text>
        <TouchableOpacity 
          onPress={flipCamera} 
          disabled={recordingState.isRecording}
          accessible={true}
          accessibilityRole="button"
          accessibilityLabel="Flip camera"
          accessibilityState={{ disabled: recordingState.isRecording }}
        >
          <Text style={styles.headerButton}>🔄</Text>
        </TouchableOpacity>
      </View>

      {/* Camera or Preview */}
      {!recordingState.videoUri ? (
        <>
          <CameraView
            ref={cameraRef}
            style={styles.camera}
            facing={cameraType}
          >
            {/* Recording Indicator */}
            {recordingState.isRecording && (
              <View style={styles.recordingIndicator}>
                <View style={styles.recordingDot} />
                <Text style={styles.recordingText}>REC</Text>
              </View>
            )}

            {/* Timer */}
            <View style={styles.timerContainer}>
              <Text style={styles.timerText}>{formatDuration(recordingState.duration)}</Text>
              {recordingState.isRecording && (
                <Text style={styles.remainingText}>{getRemainingTime()} left</Text>
              )}
            </View>
          </CameraView>

          {/* Recording Controls */}
          <VideoRecordingControls
            isRecording={recordingState.isRecording}
            isPaused={recordingState.isPaused}
            onStart={handleStartRecording}
            onPause={pauseRecording}
            onResume={resumeRecording}
            onStop={stopRecording}
          />
        </>
      ) : (
        <VideoPreview
          videoUri={recordingState.videoUri}
          duration={recordingState.duration}
          onDelete={deleteRecording}
          onSave={saveRecording}
        />
      )}
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
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 10,
  },
  headerButton: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#fff',
  },
  camera: {
    flex: 1,
  },
  recordingIndicator: {
    position: 'absolute',
    top: 100,
    left: 20,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 59, 48, 0.9)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
  },
  recordingDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#fff',
    marginRight: 6,
  },
  recordingText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  timerContainer: {
    position: 'absolute',
    top: 100,
    right: 20,
    alignItems: 'flex-end',
  },
  timerText: {
    fontSize: 24,
    fontWeight: '700',
    color: '#fff',
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: -1, height: 1 },
    textShadowRadius: 10,
    fontVariant: ['tabular-nums'],
  },
  remainingText: {
    fontSize: 12,
    color: '#fff',
    marginTop: 4,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: -1, height: 1 },
    textShadowRadius: 10,
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
