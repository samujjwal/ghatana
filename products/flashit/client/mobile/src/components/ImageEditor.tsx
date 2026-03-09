import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Text,
  Image,
  Dimensions,
  ActivityIndicator,
} from 'react-native';
import * as FileSystem from 'expo-file-system';
import { manipulateAsync, FlipType, SaveFormat } from 'expo-image-manipulator';

const { width, height } = Dimensions.get('window');

interface ImageEditorProps {
  imageUri: string;
  onComplete: (editedUri: string) => void;
  onCancel: () => void;
}

type EditAction = 'rotate' | 'flip' | 'crop';

/**
 * Image Editor Component
 * 
 * @doc.type component
 * @doc.purpose Basic image editing (crop, rotate, flip)
 * @doc.layer product
 * @doc.pattern Component
 */
export const ImageEditor: React.FC<ImageEditorProps> = ({
  imageUri,
  onComplete,
  onCancel,
}) => {
  const [editedUri, setEditedUri] = useState<string>(imageUri);
  const [rotation, setRotation] = useState<number>(0);
  const [isProcessing, setIsProcessing] = useState(false);

  const rotateImage = async () => {
    setIsProcessing(true);
    try {
      const newRotation = (rotation + 90) % 360;
      const manipResult = await manipulateAsync(
        editedUri,
        [{ rotate: 90 }],
        { compress: 1, format: SaveFormat.JPEG }
      );
      setEditedUri(manipResult.uri);
      setRotation(newRotation);
    } catch (error) {
      console.error('Error rotating image:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  const flipHorizontal = async () => {
    setIsProcessing(true);
    try {
      const manipResult = await manipulateAsync(
        editedUri,
        [{ flip: FlipType.Horizontal }],
        { compress: 1, format: SaveFormat.JPEG }
      );
      setEditedUri(manipResult.uri);
    } catch (error) {
      console.error('Error flipping image:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  const flipVertical = async () => {
    setIsProcessing(true);
    try {
      const manipResult = await manipulateAsync(
        editedUri,
        [{ flip: FlipType.Vertical }],
        { compress: 1, format: SaveFormat.JPEG }
      );
      setEditedUri(manipResult.uri);
    } catch (error) {
      console.error('Error flipping image:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  const cropToSquare = async () => {
    setIsProcessing(true);
    try {
      const imageInfo = await FileSystem.getInfoAsync(editedUri);
      if (!imageInfo.exists) return;

      // Get image dimensions from Image component
      Image.getSize(editedUri, async (imgWidth, imgHeight) => {
        const size = Math.min(imgWidth, imgHeight);
        const originX = (imgWidth - size) / 2;
        const originY = (imgHeight - size) / 2;

        const manipResult = await manipulateAsync(
          editedUri,
          [
            {
              crop: {
                originX,
                originY,
                width: size,
                height: size,
              },
            },
          ],
          { compress: 1, format: SaveFormat.JPEG }
        );
        setEditedUri(manipResult.uri);
        setIsProcessing(false);
      });
    } catch (error) {
      console.error('Error cropping image:', error);
      setIsProcessing(false);
    }
  };

  const handleComplete = () => {
    onComplete(editedUri);
  };

  return (
    <View style={styles.container}>
      {/* Image Preview */}
      <View style={styles.imageContainer}>
        {isProcessing && (
          <View style={styles.processingOverlay}>
            <ActivityIndicator size="large" color="#007aff" />
            <Text style={styles.processingText}>Processing...</Text>
          </View>
        )}
        <Image
          source={{ uri: editedUri }}
          style={styles.image}
          resizeMode="contain"
        />
      </View>

      {/* Edit Tools */}
      <View style={styles.toolsContainer}>
        <Text style={styles.toolsTitle}>Edit Tools</Text>
        <View style={styles.tools}>
          <TouchableOpacity
            style={styles.toolButton}
            onPress={rotateImage}
            disabled={isProcessing}
          >
            <Text style={styles.toolIcon}>🔄</Text>
            <Text style={styles.toolText}>Rotate</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.toolButton}
            onPress={flipHorizontal}
            disabled={isProcessing}
          >
            <Text style={styles.toolIcon}>↔️</Text>
            <Text style={styles.toolText}>Flip H</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.toolButton}
            onPress={flipVertical}
            disabled={isProcessing}
          >
            <Text style={styles.toolIcon}>↕️</Text>
            <Text style={styles.toolText}>Flip V</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.toolButton}
            onPress={cropToSquare}
            disabled={isProcessing}
          >
            <Text style={styles.toolIcon}>✂️</Text>
            <Text style={styles.toolText}>Crop</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Action Buttons */}
      <View style={styles.actions}>
        <TouchableOpacity
          style={styles.cancelButton}
          onPress={onCancel}
          disabled={isProcessing}
        >
          <Text style={styles.cancelButtonText}>Cancel</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.doneButton}
          onPress={handleComplete}
          disabled={isProcessing}
        >
          <Text style={styles.doneButtonText}>Done</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  imageContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative',
  },
  image: {
    width: width,
    height: height * 0.6,
  },
  processingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10,
  },
  processingText: {
    color: '#fff',
    marginTop: 12,
    fontSize: 16,
  },
  toolsContainer: {
    backgroundColor: '#1a1a1a',
    paddingVertical: 20,
    paddingHorizontal: 16,
  },
  toolsTitle: {
    color: '#888',
    fontSize: 14,
    marginBottom: 12,
    textTransform: 'uppercase',
  },
  tools: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  toolButton: {
    alignItems: 'center',
    padding: 12,
  },
  toolIcon: {
    fontSize: 32,
    marginBottom: 4,
  },
  toolText: {
    color: '#fff',
    fontSize: 12,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 20,
    backgroundColor: '#000',
  },
  cancelButton: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    backgroundColor: '#333',
    marginRight: 10,
    alignItems: 'center',
  },
  cancelButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
  doneButton: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    backgroundColor: '#007aff',
    marginLeft: 10,
    alignItems: 'center',
  },
  doneButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
});
