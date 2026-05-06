import React from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Text,
  ActivityIndicator,
} from 'react-native';
import { CameraView, Camera, CameraType } from 'expo-camera';
import { flashitMobileTheme } from '../theme/kernelTheme';

interface CameraPreviewProps {
  cameraRef: React.RefObject<any>;
  cameraType: CameraType;
  onCapture: () => void;
  onFlip: () => void;
  onGallery: () => void;
  isProcessing: boolean;
}

/**
 * Camera Preview Component
 * 
 * @doc.type component
 * @doc.purpose Camera preview with capture controls
 * @doc.layer product
 * @doc.pattern Component
 */
export const CameraPreview: React.FC<CameraPreviewProps> = ({
  cameraRef,
  cameraType,
  onCapture,
  onFlip,
  onGallery,
  isProcessing,
}) => {
  return (
    <View style={styles.container}>
      <CameraView
        ref={cameraRef}
        style={styles.camera}
        facing={cameraType}
      />

      {/* Camera Overlay - Positioned Absolutely */}
      <View style={styles.overlay}>
        {/* Grid Lines (Rule of Thirds) */}
        <View style={styles.gridContainer}>
          <View style={styles.gridLineVertical} />
          <View style={styles.gridLineVertical} />
          <View style={styles.gridLineHorizontal} />
          <View style={styles.gridLineHorizontal} />
        </View>
      </View>

      {/* Camera Controls */}
      <View style={styles.controls}>
        {/* Gallery Button */}
        <TouchableOpacity
          style={styles.galleryButton}
          onPress={onGallery}
          disabled={isProcessing}
        >
          <Text style={styles.galleryButtonText}>📷</Text>
        </TouchableOpacity>

        {/* Capture Button */}
        <TouchableOpacity
          style={[styles.captureButton, isProcessing && styles.captureButtonDisabled]}
          onPress={onCapture}
          disabled={isProcessing}
        >
          {isProcessing ? (
            <ActivityIndicator color={flashitMobileTheme.text.inverse} size="large" />
          ) : (
            <View style={styles.captureButtonInner} />
          )}
        </TouchableOpacity>

        {/* Flip Camera Button */}
        <TouchableOpacity
          style={styles.flipButton}
          onPress={onFlip}
          disabled={isProcessing}
        >
          <Text style={styles.flipButtonText}>🔄</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  camera: {
    flex: 1,
  },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'transparent',
  },
  gridContainer: {
    flex: 1,
    position: 'relative',
  },
  gridLineVertical: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 1,
    backgroundColor: flashitMobileTheme.border,
    left: '33.33%',
  },
  gridLineHorizontal: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: flashitMobileTheme.border,
    top: '33.33%',
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingVertical: 30,
    paddingHorizontal: 20,
    backgroundColor: flashitMobileTheme.shadow.color,
  },
  galleryButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: flashitMobileTheme.text.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  galleryButtonText: {
    fontSize: 32,
  },
  captureButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: flashitMobileTheme.text.inverse,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 5,
    borderColor: flashitMobileTheme.text.primary,
  },
  captureButtonDisabled: {
    opacity: 0.5,
  },
  captureButtonInner: {
    width: 70,
    height: 70,
    borderRadius: 35,
    backgroundColor: flashitMobileTheme.text.inverse,
  },
  flipButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: flashitMobileTheme.text.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  flipButtonText: {
    fontSize: 32,
  },
});
