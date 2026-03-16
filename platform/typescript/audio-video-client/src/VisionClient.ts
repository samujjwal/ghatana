import type {
  VisionRequest,
  VisionResult,
  ServiceHealth,
  PaginatedResponse,
} from '@ghatana/audio-video-types';
import { BaseHttpClient, type ClientConfig } from './BaseHttpClient.js';
import { AudioVideoServiceError } from '@ghatana/audio-video-types';

/**
 * REST client for the Ghatana Vision inference service.
 *
 * @example
 * ```ts
 * const client = new VisionClient({ baseUrl, getToken });
 * const result = await client.analyze({
 *   media: imageBuffer,
 *   mediaType: 'jpeg',
 *   task: 'object_detection',
 *   options: { minConfidence: 0.5 },
 * });
 * for (const frame of result.frames) {
 *   console.log(frame.detections);
 * }
 * ```
 */
export class VisionClient extends BaseHttpClient {
  constructor(config: ClientConfig) {
    super(config);
  }

  /**
   * Analyze an image or video clip.
   *
   * If `request.media` is an `ArrayBuffer` (image / video bytes) they are sent
   * as `multipart/form-data`.  If it is a `string` it is treated as a URL or
   * base-64 data URL and sent as JSON.
   */
  async analyze(request: VisionRequest, signal?: AbortSignal): Promise<VisionResult> {
    let body: BodyInit;
    const extraHeaders: Record<string, string> = {};

    if (typeof request.media === 'string') {
      body = JSON.stringify({
        mediaUrl: request.media,
        mediaType: request.mediaType,
        task: request.task,
        options: request.options ?? {},
      });
      extraHeaders['Content-Type'] = 'application/json';
    } else {
      const form = new FormData();
      form.append('media', new Blob([request.media], { type: `image/${request.mediaType}` }), 'media');
      form.append('mediaType', request.mediaType);
      form.append('task', request.task);
      if (request.options) form.append('options', JSON.stringify(request.options));
      body = form;
    }

    return this.fetch<VisionResult>('/v1/vision/analyze', {
      method: 'POST',
      headers: extraHeaders,
      body,
      signal,
    });
  }

  /**
   * Stream vision analysis for a live video `MediaStream`.
   *
   * Frames are captured and sent as JPEG snapshots every `intervalMs` ms.
   * The server must expose `POST /v1/vision/analyze/stream` with SSE.
   */
  async *analyzeStream(
    stream: MediaStream,
    task: VisionRequest['task'],
    { intervalMs = 1000, minConfidence = 0.4 }: { intervalMs?: number; minConfidence?: number } = {},
    signal?: AbortSignal,
  ): AsyncGenerator<VisionResult> {
    const token = await this.getToken();
    const track = stream.getVideoTracks()[0];
    if (!track) throw new AudioVideoServiceError('INVALID_INPUT', 'No video track in stream');

    // Use ImageCapture API if available; fall back to canvas-based capture.
    const capture =
      typeof ImageCapture !== 'undefined'
        ? new ImageCapture(track)
        : null;

    const offscreen = document.createElement('canvas');
    const offCtx = offscreen.getContext('2d');

    // Wrap in a local async gen that polls frames
    while (!signal?.aborted) {
      await sleep(intervalMs);
      if (signal?.aborted) break;

      let jpeg: Blob;
      if (capture) {
        try {
          const bitmap = await capture.grabFrame();
          offscreen.width = bitmap.width;
          offscreen.height = bitmap.height;
          offCtx?.drawImage(bitmap, 0, 0);
          jpeg = await blobFromCanvas(offscreen);
          bitmap.close();
        } catch {
          continue;
        }
      } else {
        // Canvas fallback is not possible without a video element reference.
        // consumers should prefer `analyze()` with periodic snapshots.
        throw new AudioVideoServiceError('UNSUPPORTED_FORMAT', 'ImageCapture API not available');
      }

      const form = new FormData();
      form.append('media', jpeg, 'frame.jpg');
      form.append('mediaType', 'jpeg');
      form.append('task', task);
      form.append('options', JSON.stringify({ minConfidence }));

      const result = await fetch(`${this.baseUrl}/v1/vision/analyze`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: form,
        signal,
      });

      if (!result.ok) continue;
      yield (await result.json()) as VisionResult;
    }
  }

  /** List available model variants for a given vision task. */
  async listModels(task?: VisionRequest['task']): Promise<PaginatedResponse<string>> {
    const q = task ? `?task=${encodeURIComponent(task)}` : '';
    return this.fetch<PaginatedResponse<string>>(`/v1/vision/models${q}`);
  }

  /** Health check for the Vision service. */
  async health(): Promise<ServiceHealth> {
    return this.fetch<ServiceHealth>('/v1/vision/health');
  }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function blobFromCanvas(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((b) => {
      if (b) resolve(b);
      else reject(new Error('canvas.toBlob returned null'));
    }, 'image/jpeg', 0.85);
  });
}
