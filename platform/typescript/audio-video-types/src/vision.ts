import type { ImageFormat, VideoFormat } from './audio.js';

// ---------------------------------------------------------------------------
// Task types
// ---------------------------------------------------------------------------

/** The high-level computer-vision task to perform on the input media. */
export type VisionTask =
  | 'object_detection'
  | 'classification'
  | 'segmentation'
  | 'ocr'
  | 'face_detection'
  | 'pose_estimation'
  | 'depth_estimation'
  | 'action_recognition';

// ---------------------------------------------------------------------------
// Request
// ---------------------------------------------------------------------------

/** Axis-aligned bounding box in pixel coordinates (top-left origin). */
export interface BoundingBox {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

/** Fine-grained options that tune the vision pipeline. */
export interface VisionOptions {
  /** Minimum confidence threshold for detections (0.0 – 1.0). */
  readonly minConfidence?: number;
  /** Maximum number of detections to return per frame. */
  readonly maxDetections?: number;
  /** Classes to include; empty list means all classes. */
  readonly filterClasses?: readonly string[];
  /** Model variant to use on the server; omit to use the server default. */
  readonly model?: string;
  /**
   * For video inputs: sample one frame every N milliseconds.
   * Defaults to 1000 (1 fps).
   */
  readonly frameSampleIntervalMs?: number;
}

/** A single vision inference request. */
export interface VisionRequest {
  /**
   * Image/video bytes, a data URL (`data:image/jpeg;base64,...`),
   * or an HTTPS URL pointing to the media.
   */
  readonly media: ArrayBuffer | string;
  readonly mediaType: ImageFormat | VideoFormat;
  readonly task: VisionTask;
  readonly options?: VisionOptions;
}

// ---------------------------------------------------------------------------
// Result primitives
// ---------------------------------------------------------------------------

/** A single object detected within a frame. */
export interface Detection {
  readonly label: string;
  readonly confidence: number;
  readonly boundingBox: BoundingBox;
  /** Tracking identifier when processing multi-frame video. */
  readonly trackId?: number;
}

/** A top-N classification label with its confidence score. */
export interface Classification {
  readonly label: string;
  readonly confidence: number;
  /** Hierarchical label path, e.g. ['animal', 'cat', 'tabby']. */
  readonly ancestors?: readonly string[];
}

/** A single key-point in a 2-D pose skeleton. */
export interface KeyPoint {
  readonly name: string;
  readonly x: number;
  readonly y: number;
  readonly confidence: number;
}

/** Detected human pose as a set of key-points. */
export interface PoseEstimation {
  readonly keyPoints: readonly KeyPoint[];
  readonly boundingBox: BoundingBox;
  readonly confidence: number;
}

/**
 * Per-frame result for image or video vision tasks.
 * `timestampMs` is only set when the request media is a video clip.
 */
export interface VisionFrame {
  readonly frameIndex: number;
  readonly timestampMs?: number;
  readonly detections?: readonly Detection[];
  readonly classifications?: readonly Classification[];
  readonly poses?: readonly PoseEstimation[];
  /** OCR text blocks extracted from the frame. */
  readonly ocrBlocks?: readonly OCRBlock[];
}

/** A block of text localised within the image by the OCR pass. */
export interface OCRBlock {
  readonly text: string;
  readonly confidence: number;
  readonly boundingBox: BoundingBox;
  readonly language?: string;
}

/** The top-level result returned by a completed vision inference request. */
export interface VisionResult {
  readonly task: VisionTask;
  readonly frames: readonly VisionFrame[];
  readonly processingTimeMs: number;
  readonly engine: string;
  readonly modelVersion?: string;
}
