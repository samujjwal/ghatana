import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Platform,
  Image,
  ActivityIndicator,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { CameraView, Camera, CameraType } from 'expo-camera';
import * as FileSystem from 'expo-file-system/legacy';
import { useNavigation } from '@react-navigation/native';
import { CameraPreview } from '../components/CameraPreview';
import { ImageEditor } from '../components/ImageEditor';
import { optimizeImage } from '../utils/imageOptimization';

// Import MediaType for ImagePicker
import { MediaType } from 'expo-image-picker';

type CaptureMode = 'camera' | 'gallery' | 'preview' | 'edit';

interface CapturedImage {
  uri: string;
  width: number;
  height: number;
  base64?: string;
}

/**
 * Image Capture Screen for mobile photo capture
 * 
 * @doc.type screen
 * @doc.purpose Provide image capture from camera and gallery with editing
 * @doc.layer product
 * @doc.pattern Screen
 */
export const ImageCaptureScreen: React.FC = () => {
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
  const [mode, setMode] = useState<CaptureMode>('camera');
  const [cameraPermission, setCameraPermission] = useState<boolean>(false);
  const [galleryPermission, setGalleryPermission] = useState<boolean>(false);
  const [cameraType, setCameraType] = useState<CameraType>('back' as CameraType);
  const [capturedImage, setCapturedImage] = useState<CapturedImage | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const cameraRef = useRef<any>(null);

  useEffect(() => {
    requestPermissions();
  }, []);

  const requestPermissions = async () => {
    try {
      // Request camera permission
      const cameraResult = await Camera.requestCameraPermissionsAsync();
      setCameraPermission(cameraResult.status === 'granted');

      // Request media library permission
      const galleryResult = await ImagePicker.requestMediaLibraryPermissionsAsync();
      setGalleryPermission(galleryResult.status === 'granted');

      if (cameraResult.status !== 'granted') {
        Alert.alert(
          'Camera Permission Required',
          'Please grant camera permission to capture photos.',
          [{ text: 'OK' }]
        );
      }
    } catch (error) {
      console.error('Error requesting permissions:', error);
      Alert.alert('Error', 'Failed to request permissions');
    }
  };

  const capturePhoto = async () => {
    if (!cameraRef.current || !cameraPermission) return;

    try {
      setIsProcessing(true);
      const photo = await cameraRef.current.takePictureAsync({
        quality: 1,
        base64: false,
        exif: true,
      });

      setCapturedImage({
        uri: photo.uri,
        width: photo.width,
        height: photo.height,
      });
      setMode('preview');
    } catch (error) {
      console.error('Error capturing photo:', error);
      Alert.alert('Error', 'Failed to capture photo');
    } finally {
      setIsProcessing(false);
    }
  };

  const pickFromGallery = async () => {
    if (!galleryPermission) {
      await requestPermissions();
      return;
    }

    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: 'images' as any,
        allowsEditing: false,
        quality: 1,
        exif: true,
      });

      if (!result.canceled && result.assets[0]) {
        const asset = result.assets[0];
        setCapturedImage({
          uri: asset.uri,
          width: asset.width,
          height: asset.height,
        });
        setMode('preview');
      }
    } catch (error) {
      console.error('Error picking image:', error);
      Alert.alert('Error', 'Failed to pick image from gallery');
    }
  };

  const flipCamera = () => {
    setCameraType((current) =>
      current === 'back' ? 'front' as CameraType : 'back' as CameraType
    );
  };

  const retakePhoto = () => {
    setCapturedImage(null);
    setMode('camera');
  };

  const editPhoto = () => {
    setMode('edit');
  };

  const savePhoto = async () => {
    if (!capturedImage) return;

    try {
      setIsProcessing(true);

      // Optimize image
      const optimizedUri = await optimizeImage(capturedImage.uri, {
        maxWidth: 2048,
        maxHeight: 2048,
        quality: 0.9,
      });

      // Generate filename
      const timestamp = new Date().getTime();
      const filename = `photo_${timestamp}.jpg`;
      const destUri = `${FileSystem.documentDirectory}${filename}`;

      // Copy to permanent storage
      await FileSystem.copyAsync({
        from: optimizedUri,
        to: destUri,
      });

      // Get file info
      const fileInfo = await FileSystem.getInfoAsync(destUri);
      const fileSizeMB = fileInfo.size ? (fileInfo.size / (1024 * 1024)).toFixed(2) : '0';

      Alert.alert(
        'Photo Saved',
        `Your photo has been saved (${fileSizeMB} MB)`,
        [
          {
            text: 'OK',
            onPress: () => {
              // @ts-ignore - navigation type
              navigation.navigate('Capture', { imageUri: destUri });
            },
          },
        ]
      );
    } catch (error) {
      console.error('Error saving photo:', error);
      Alert.alert('Error', 'Failed to save photo');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleEditComplete = (editedUri: string) => {
    setCapturedImage((prev) =>
      prev ? { ...prev, uri: editedUri } : null
    );
    setMode('preview');
  };

  if (!cameraPermission && mode === 'camera') {
    return (
      <View style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionTitle}>Camera Access Required</Text>
          <Text style={styles.permissionText}>
            To capture photos, please grant camera permission.
          </Text>
          <TouchableOpacity 
            style={styles.permissionButton} 
            onPress={requestPermissions}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Grant camera permission"
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
          accessibilityLabel="Cancel capture"
        >
          <Text style={styles.headerButton}>Cancel</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>
          {mode === 'camera' ? 'Camera' : mode === 'gallery' ? 'Gallery' : 'Preview'}
        </Text>
        <View style={styles.headerButton} />
      </View>

      {/* Camera View */}
      {mode === 'camera' && cameraPermission && (
        <CameraPreview
          cameraRef={cameraRef}
          cameraType={cameraType}
          onCapture={capturePhoto}
          onFlip={flipCamera}
          onGallery={pickFromGallery}
          isProcessing={isProcessing}
        />
      )}

      {/* Preview Mode */}
      {mode === 'preview' && capturedImage && (
        <View style={styles.previewContainer}>
          <Image
            source={{ uri: capturedImage.uri }}
            style={styles.previewImage}
            resizeMode="contain"
          />
          <View style={styles.previewControls}>
            <TouchableOpacity 
              style={styles.previewButton} 
              onPress={retakePhoto}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Retake photo"
            >
              <Text style={styles.previewButtonText}>Retake</Text>
            </TouchableOpacity>
            <TouchableOpacity 
              style={styles.previewButton} 
              onPress={editPhoto}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Edit photo"
            >
              <Text style={styles.previewButtonText}>Edit</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.previewButton, styles.saveButton]}
              onPress={savePhoto}
              disabled={isProcessing}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel="Save photo and use it"
              accessibilityState={{ disabled: isProcessing, busy: isProcessing }}
            >
              {isProcessing ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={[styles.previewButtonText, styles.saveButtonText]}>
                  Save & Use
                </Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* Edit Mode */}
      {mode === 'edit' && capturedImage && (
        <ImageEditor
          imageUri={capturedImage.uri}
          onComplete={handleEditComplete}
          onCancel={() => setMode('preview')}
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
    width: 60,
    color: '#fff',
    fontSize: 16,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#fff',
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
  previewContainer: {
    flex: 1,
  },
  previewImage: {
    flex: 1,
    width: '100%',
  },
  previewControls: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 20,
    paddingBottom: Platform.OS === 'ios' ? 40 : 20,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
  },
  previewButton: {
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    backgroundColor: '#333',
    minWidth: 100,
    alignItems: 'center',
  },
  previewButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  saveButton: {
    backgroundColor: '#007aff',
  },
  saveButtonText: {
    color: '#fff',
  },
});
