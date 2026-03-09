import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';
import * as FileSystem from 'expo-file-system';

interface OptimizationOptions {
  maxWidth?: number;
  maxHeight?: number;
  quality?: number;
  format?: SaveFormat;
}

const DEFAULT_OPTIONS: Required<OptimizationOptions> = {
  maxWidth: 2048,
  maxHeight: 2048,
  quality: 0.9,
  format: SaveFormat.JPEG,
};

/**
 * Image Optimization Utility
 * 
 * @doc.type utility
 * @doc.purpose Optimize images for upload (resize, compress)
 * @doc.layer product
 * @doc.pattern Utility
 */

/**
 * Optimize an image by resizing and compressing
 * @param uri - Source image URI
 * @param options - Optimization options
 * @returns Optimized image URI
 */
export async function optimizeImage(
  uri: string,
  options: OptimizationOptions = {}
): Promise<string> {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  try {
    // Get image dimensions
    const imageInfo = await getImageDimensions(uri);
    if (!imageInfo) {
      throw new Error('Failed to get image dimensions');
    }

    const { width, height } = imageInfo;

    // Calculate new dimensions maintaining aspect ratio
    const newDimensions = calculateOptimalDimensions(
      width,
      height,
      opts.maxWidth,
      opts.maxHeight
    );

    // Only resize if image exceeds max dimensions
    const actions = [];
    if (newDimensions.width < width || newDimensions.height < height) {
      actions.push({
        resize: newDimensions,
      });
    }

    // Manipulate image
    const result = await manipulateAsync(uri, actions, {
      compress: opts.quality,
      format: opts.format,
    });

    return result.uri;
  } catch (error) {
    console.error('Error optimizing image:', error);
    // Return original URI if optimization fails
    return uri;
  }
}

/**
 * Get image dimensions
 * @param uri - Image URI
 * @returns Width and height, or null if failed
 */
function getImageDimensions(
  uri: string
): Promise<{ width: number; height: number } | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      resolve({ width: img.width, height: img.height });
    };
    img.onerror = () => {
      resolve(null);
    };
    img.src = uri;
  });
}

/**
 * Calculate optimal dimensions maintaining aspect ratio
 * @param width - Original width
 * @param height - Original height
 * @param maxWidth - Maximum width
 * @param maxHeight - Maximum height
 * @returns Optimized dimensions
 */
function calculateOptimalDimensions(
  width: number,
  height: number,
  maxWidth: number,
  maxHeight: number
): { width: number; height: number } {
  if (width <= maxWidth && height <= maxHeight) {
    return { width, height };
  }

  const aspectRatio = width / height;

  if (width > height) {
    // Landscape
    const newWidth = Math.min(width, maxWidth);
    const newHeight = Math.round(newWidth / aspectRatio);
    return {
      width: newWidth,
      height: Math.min(newHeight, maxHeight),
    };
  } else {
    // Portrait or square
    const newHeight = Math.min(height, maxHeight);
    const newWidth = Math.round(newHeight * aspectRatio);
    return {
      width: Math.min(newWidth, maxWidth),
      height: newHeight,
    };
  }
}

/**
 * Get file size in MB
 * @param uri - File URI
 * @returns File size in MB, or null if failed
 */
export async function getFileSizeMB(uri: string): Promise<number | null> {
  try {
    const fileInfo = await FileSystem.getInfoAsync(uri);
    if (fileInfo.exists && fileInfo.size) {
      return fileInfo.size / (1024 * 1024);
    }
    return null;
  } catch (error) {
    console.error('Error getting file size:', error);
    return null;
  }
}

/**
 * Convert image to base64
 * @param uri - Image URI
 * @returns Base64 string, or null if failed
 */
export async function imageToBase64(uri: string): Promise<string | null> {
  try {
    const base64 = await FileSystem.readAsStringAsync(uri, {
      encoding: FileSystem.EncodingType.Base64,
    });
    return base64;
  } catch (error) {
    console.error('Error converting image to base64:', error);
    return null;
  }
}

/**
 * Compress image to target file size
 * @param uri - Image URI
 * @param targetSizeMB - Target size in MB
 * @returns Compressed image URI
 */
export async function compressToSize(
  uri: string,
  targetSizeMB: number
): Promise<string> {
  let currentUri = uri;
  let quality = 0.9;
  let attempts = 0;
  const maxAttempts = 5;

  while (attempts < maxAttempts) {
    const size = await getFileSizeMB(currentUri);
    if (size && size <= targetSizeMB) {
      return currentUri;
    }

    // Reduce quality
    quality -= 0.15;
    if (quality < 0.3) {
      quality = 0.3; // Don't go below 30% quality
    }

    const result = await manipulateAsync(currentUri, [], {
      compress: quality,
      format: SaveFormat.JPEG,
    });

    currentUri = result.uri;
    attempts++;
  }

  return currentUri;
}
