/**
 * @doc.type client
 * @doc.purpose Unified service client for audio-video application
 * @doc.layer shared
 * @doc.pattern facade pattern
 */

import type {
  ServiceType,
  STTRequest,
  STTResult,
  TTSRequest,
  TTSResult,
  AIVoiceRequest,
  AIVoiceResult,
  VisionRequest,
  DetectionResult,
  MultimodalRequest,
  MultimodalResult,
  ServiceStatus,
  ServiceResponse,
  AudioVideoError,
  ProgressCallback,
  ErrorCallback,
  SuccessCallback
} from '@ghatana/audio-video-types';

/**
 * Configuration for service clients
 */
export interface ServiceClientConfig {
  endpoint: string;
  timeout: number;
  retries: number;
  enableLogging: boolean;
  apiKey?: string;
}

/**
 * Internal fetch options for service calls.
 */
interface ServiceCallOptions<T> {
  method: 'GET' | 'POST';
  path: string;
  body?: unknown;
  config: ServiceClientConfig;
  serviceLabel: string;
}

/**
 * Unified audio-video service client.
 *
 * <p>Calls the audio-video REST HTTP gateway at each service's configured endpoint.
 * The default ports (50051-50055) are gRPC ports — configure your endpoint to point
 * to a gRPC-Web proxy or HTTP REST transcoding gateway for TypeScript consumers.
 * In local dev, an HTTP gateway is expected at the service's 8080 port.
 */
export class AudioVideoClient {
  private configs: Map<ServiceType, ServiceClientConfig>;
  private eventListeners: Map<string, Function[]> = new Map();

  constructor(configs: Record<ServiceType, ServiceClientConfig>) {
    this.configs = new Map(Object.entries(configs) as [ServiceType, ServiceClientConfig][]);
  }

  /**
   * Speech-to-Text transcription
   */
  async transcribe(request: STTRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<STTResult>> {
    const config = this.requireConfig('stt');
    this.emitEvent('stt:transcription:start', { request });
    try {
      const result = await this.callService<STTResult>({
        method: 'POST',
        path: '/api/stt/transcribe',
        body: request,
        config,
        serviceLabel: 'stt',
      });
      this.emitEvent('stt:transcription:complete', { request, result });
      return { success: true, data: result, metadata: { processingTime: result.processingTimeMs, service: 'stt' } };
    } catch (error) {
      const audioVideoError = this.toError(error, 'STT_ERROR', 'stt');
      this.emitEvent('stt:transcription:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);
      return { success: false, error: audioVideoError };
    }
  }

  /**
   * Text-to-Speech synthesis
   */
  async synthesize(request: TTSRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<TTSResult>> {
    const config = this.requireConfig('tts');
    this.emitEvent('tts:synthesis:start', { request });
    try {
      const result = await this.callService<TTSResult>({
        method: 'POST',
        path: '/api/tts/synthesize',
        body: request,
        config,
        serviceLabel: 'tts',
      });
      this.emitEvent('tts:synthesis:complete', { request, result });
      return { success: true, data: result, metadata: { processingTime: result.processingTimeMs, service: 'tts' } };
    } catch (error) {
      const audioVideoError = this.toError(error, 'TTS_ERROR', 'tts');
      this.emitEvent('tts:synthesis:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);
      return { success: false, error: audioVideoError };
    }
  }

  /**
   * AI Voice processing
   */
  async processAIVoice(request: AIVoiceRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<AIVoiceResult>> {
    const config = this.requireConfig('ai-voice');
    this.emitEvent('ai-voice:process:start', { request });
    try {
      const result = await this.callService<AIVoiceResult>({
        method: 'POST',
        path: '/api/ai-voice/process',
        body: request,
        config,
        serviceLabel: 'ai-voice',
      });
      this.emitEvent('ai-voice:process:complete', { request, result });
      return { success: true, data: result, metadata: { processingTime: result.processingTimeMs, service: 'ai-voice' } };
    } catch (error) {
      const audioVideoError = this.toError(error, 'AI_VOICE_ERROR', 'ai-voice');
      this.emitEvent('ai-voice:process:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);
      return { success: false, error: audioVideoError };
    }
  }

  /**
   * Computer Vision processing
   */
  async processVision(request: VisionRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<DetectionResult>> {
    const config = this.requireConfig('vision');
    this.emitEvent('vision:process:start', { request });
    try {
      const result = await this.callService<DetectionResult>({
        method: 'POST',
        path: '/api/vision/analyze',
        body: request,
        config,
        serviceLabel: 'vision',
      });
      this.emitEvent('vision:process:complete', { request, result });
      return { success: true, data: result, metadata: { processingTime: result.processingTimeMs, service: 'vision' } };
    } catch (error) {
      const audioVideoError = this.toError(error, 'VISION_ERROR', 'vision');
      this.emitEvent('vision:process:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);
      return { success: false, error: audioVideoError };
    }
  }

  /**
   * Multimodal processing
   */
  async processMultimodal(request: MultimodalRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<MultimodalResult>> {
    const config = this.requireConfig('multimodal');
    this.emitEvent('multimodal:process:start', { request });
    try {
      const result = await this.callService<MultimodalResult>({
        method: 'POST',
        path: '/api/multimodal/process',
        body: request,
        config,
        serviceLabel: 'multimodal',
      });
      this.emitEvent('multimodal:process:complete', { request, result });
      return { success: true, data: result, metadata: { processingTime: result.processingTimeMs, service: 'multimodal' } };
    } catch (error) {
      const audioVideoError = this.toError(error, 'MULTIMODAL_ERROR', 'multimodal');
      this.emitEvent('multimodal:process:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);
      return { success: false, error: audioVideoError };
    }
  }

  /**
   * Get service health status.
   * Calls GET {endpoint}/health on the service's HTTP health port.
   */
  async getServiceStatus(service: ServiceType): Promise<ServiceStatus> {
    const config = this.requireConfig(service);
    try {
      const data = await this.callService<Record<string, unknown>>({
        method: 'GET',
        path: '/health',
        config,
        serviceLabel: service,
      });
      return {
        service,
        status: (data['status'] as string) === 'UP' ? 'healthy' : 'degraded',
        uptime: (data['uptime'] as number) ?? Date.now(),
        version: (data['version'] as string) ?? '1.0.0',
        lastCheck: new Date(),
        metrics: {
          requestCount: (data['requestCount'] as number) ?? 0,
          errorRate: (data['errorRate'] as number) ?? 0,
          avgResponseTime: (data['avgResponseTime'] as number) ?? 0,
          activeConnections: (data['activeConnections'] as number) ?? 0,
        },
      };
    } catch {
      return {
        service,
        status: 'unhealthy',
        uptime: 0,
        version: 'unknown',
        lastCheck: new Date(),
        metrics: { requestCount: 0, errorRate: 1, avgResponseTime: 0, activeConnections: 0 },
      };
    }
  }

  /**
   * Get all services status
   */
  async getAllServicesStatus(): Promise<ServiceStatus[]> {
    const services: ServiceType[] = ['stt', 'tts', 'ai-voice', 'vision', 'multimodal'];
    return Promise.all(services.map(s => this.getServiceStatus(s)));
  }

  // ─── Event handling ───────────────────────────────────────────────────────

  addEventListener(event: string, callback: Function): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(callback);
  }

  removeEventListener(event: string, callback: Function): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(callback);
      if (index > -1) listeners.splice(index, 1);
    }
  }

  private emitEvent(event: string, data: unknown): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(callback => {
        try { callback(data); } catch (error) {
          console.error(`Error in event listener for ${event}:`, error);
        }
      });
    }
  }

  // ─── Internal HTTP helpers ────────────────────────────────────────────────

  private requireConfig(service: ServiceType): ServiceClientConfig {
    const config = this.configs.get(service);
    if (!config) throw new Error(`${service} service not configured`);
    return config;
  }

  /**
   * Makes one HTTP call to the service endpoint with timeout, retries, and
   * optional API-key injection. Throws on non-2xx or parsed error body.
   */
  private async callService<T>(opts: ServiceCallOptions<T>): Promise<T> {
    const { method, path, body, config, serviceLabel } = opts;
    const url = `${config.endpoint}${path}`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (config.apiKey) headers['Authorization'] = `Bearer ${config.apiKey}`;

    let lastError: Error = new Error('No attempts made');
    const maxAttempts = config.retries + 1;

    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      if (attempt > 0) {
        // exponential back-off: 200ms, 400ms, 800ms, …
        await new Promise(r => setTimeout(r, 200 * Math.pow(2, attempt - 1)));
      }

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), config.timeout);

      try {
        const response = await fetch(url, {
          method,
          headers,
          body: body !== undefined ? JSON.stringify(body) : undefined,
          signal: controller.signal,
        });
        clearTimeout(timeoutId);

        if (!response.ok) {
          const errorText = await response.text().catch(() => '');
          throw new Error(`HTTP ${response.status} from ${serviceLabel}: ${errorText}`);
        }

        return await response.json() as T;
      } catch (err) {
        clearTimeout(timeoutId);
        lastError = err instanceof Error ? err : new Error(String(err));
        if (config.enableLogging) {
          console.warn(`[${serviceLabel}] attempt ${attempt + 1}/${maxAttempts} failed:`, lastError.message);
        }
        // Don't retry on client errors (4xx)
        if (lastError.message.includes('HTTP 4')) break;
      }
    }

    throw lastError;
  }

  private toError(error: unknown, code: string, service: ServiceType): AudioVideoError {
    return {
      code,
      message: error instanceof Error ? error.message : 'Unknown error',
      service,
      timestamp: new Date(),
    };
  }
}

/**
 * Factory function to create configured client
 */
export function createAudioVideoClient(configs: Record<ServiceType, ServiceClientConfig>): AudioVideoClient {
  return new AudioVideoClient(configs);
}

/**
 * Default configuration for development.
 *
 * NOTE: Ports 50051-50055 are gRPC ports. For TypeScript consumers, configure
 * the endpoint to point to an HTTP REST gateway or gRPC-Web proxy instead.
 * Use port 8080 for the HTTP REST/health endpoints in development.
 */
export const defaultConfigs: Record<ServiceType, ServiceClientConfig> = {
  stt: {
    endpoint: 'http://localhost:8081',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  tts: {
    endpoint: 'http://localhost:8082',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  'ai-voice': {
    endpoint: 'http://localhost:8083',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  vision: {
    endpoint: 'http://localhost:8084',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  multimodal: {
    endpoint: 'http://localhost:8085',
    timeout: 60000,
    retries: 3,
    enableLogging: true
  }
};

  ServiceType,
  STTRequest,
  STTResult,
  TTSRequest,
  TTSResult,
  AIVoiceRequest,
  AIVoiceResult,
  VisionRequest,
  DetectionResult,
  MultimodalRequest,
  MultimodalResult,
  ServiceStatus,
  ServiceResponse,
  AudioVideoError,
  ProgressCallback,
  ErrorCallback,
  SuccessCallback
} from '@ghatana/audio-video-types';

/**
 * Configuration for service clients
 */
export interface ServiceClientConfig {
  endpoint: string;
  timeout: number;
  retries: number;
  enableLogging: boolean;
  apiKey?: string;
}

/**
 * Unified audio-video service client
 */
export class AudioVideoClient {
  private configs: Map<ServiceType, ServiceClientConfig>;
  private eventListeners: Map<string, Function[]> = new Map();

  constructor(configs: Record<ServiceType, ServiceClientConfig>) {
    this.configs = new Map(Object.entries(configs) as [ServiceType, ServiceClientConfig][]);
  }

  /**
   * Speech-to-Text transcription
   */
  async transcribe(request: STTRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<STTResult>> {
    try {
      const config = this.configs.get('stt');
      if (!config) {
        throw new Error('STT service not configured');
      }

      // Emit progress event
      this.emitEvent('stt:transcription:start', { request });

      // TODO: Implement actual gRPC call to STT service
      const result = await this.mockSTTCall(request);

      // Emit success event
      this.emitEvent('stt:transcription:complete', { request, result });

      return {
        success: true,
        data: result,
        metadata: {
          processingTime: result.processingTimeMs,
          service: 'stt'
        }
      };

    } catch (error) {
      const audioVideoError: AudioVideoError = {
        code: 'STT_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
        service: 'stt',
        timestamp: new Date()
      };

      this.emitEvent('stt:transcription:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);

      return {
        success: false,
        error: audioVideoError
      };
    }
  }

  /**
   * Text-to-Speech synthesis
   */
  async synthesize(request: TTSRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<TTSResult>> {
    try {
      const config = this.configs.get('tts');
      if (!config) {
        throw new Error('TTS service not configured');
      }

      this.emitEvent('tts:synthesis:start', { request });

      // TODO: Implement actual gRPC call to TTS service
      const result = await this.mockTTSCall(request);

      this.emitEvent('tts:synthesis:complete', { request, result });

      return {
        success: true,
        data: result,
        metadata: {
          processingTime: result.processingTimeMs,
          service: 'tts'
        }
      };

    } catch (error) {
      const audioVideoError: AudioVideoError = {
        code: 'TTS_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
        service: 'tts',
        timestamp: new Date()
      };

      this.emitEvent('tts:synthesis:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);

      return {
        success: false,
        error: audioVideoError
      };
    }
  }

  /**
   * AI Voice processing
   */
  async processAIVoice(request: AIVoiceRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<AIVoiceResult>> {
    try {
      const config = this.configs.get('ai-voice');
      if (!config) {
        throw new Error('AI Voice service not configured');
      }

      this.emitEvent('ai-voice:process:start', { request });

      // TODO: Implement actual gRPC call to AI Voice service
      const result = await this.mockAIVoiceCall(request);

      this.emitEvent('ai-voice:process:complete', { request, result });

      return {
        success: true,
        data: result,
        metadata: {
          processingTime: result.processingTimeMs,
          service: 'ai-voice'
        }
      };

    } catch (error) {
      const audioVideoError: AudioVideoError = {
        code: 'AI_VOICE_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
        service: 'ai-voice',
        timestamp: new Date()
      };

      this.emitEvent('ai-voice:process:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);

      return {
        success: false,
        error: audioVideoError
      };
    }
  }

  /**
   * Computer Vision processing
   */
  async processVision(request: VisionRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<DetectionResult>> {
    try {
      const config = this.configs.get('vision');
      if (!config) {
        throw new Error('Vision service not configured');
      }

      this.emitEvent('vision:process:start', { request });

      // TODO: Implement actual gRPC call to Vision service
      const result = await this.mockVisionCall(request);

      this.emitEvent('vision:process:complete', { request, result });

      return {
        success: true,
        data: result,
        metadata: {
          processingTime: result.processingTimeMs,
          service: 'vision'
        }
      };

    } catch (error) {
      const audioVideoError: AudioVideoError = {
        code: 'VISION_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
        service: 'vision',
        timestamp: new Date()
      };

      this.emitEvent('vision:process:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);

      return {
        success: false,
        error: audioVideoError
      };
    }
  }

  /**
   * Multimodal processing
   */
  async processMultimodal(request: MultimodalRequest, options?: {
    onProgress?: ProgressCallback;
    onError?: ErrorCallback;
  }): Promise<ServiceResponse<MultimodalResult>> {
    try {
      const config = this.configs.get('multimodal');
      if (!config) {
        throw new Error('Multimodal service not configured');
      }

      this.emitEvent('multimodal:process:start', { request });

      // TODO: Implement actual gRPC call to Multimodal service
      const result = await this.mockMultimodalCall(request);

      this.emitEvent('multimodal:process:complete', { request, result });

      return {
        success: true,
        data: result,
        metadata: {
          processingTime: result.processingTimeMs,
          service: 'multimodal'
        }
      };

    } catch (error) {
      const audioVideoError: AudioVideoError = {
        code: 'MULTIMODAL_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
        service: 'multimodal',
        timestamp: new Date()
      };

      this.emitEvent('multimodal:process:error', { request, error: audioVideoError });
      options?.onError?.(audioVideoError);

      return {
        success: false,
        error: audioVideoError
      };
    }
  }

  /**
   * Get service status
   */
  async getServiceStatus(service: ServiceType): Promise<ServiceStatus> {
    const config = this.configs.get(service);
    if (!config) {
      throw new Error(`${service} service not configured`);
    }

    // TODO: Implement actual health check
    return {
      service,
      status: 'healthy',
      uptime: Date.now(),
      version: '1.0.0',
      lastCheck: new Date(),
      metrics: {
        requestCount: 0,
        errorRate: 0,
        avgResponseTime: 150,
        activeConnections: 1
      }
    };
  }

  /**
   * Get all services status
   */
  async getAllServicesStatus(): Promise<ServiceStatus[]> {
    const services: ServiceType[] = ['stt', 'tts', 'ai-voice', 'vision', 'multimodal'];
    const statuses = await Promise.all(
      services.map(service => this.getServiceStatus(service))
    );
    return statuses;
  }

  /**
   * Event handling
   */
  addEventListener(event: string, callback: Function): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(callback);
  }

  removeEventListener(event: string, callback: Function): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(callback);
      if (index > -1) {
        listeners.splice(index, 1);
      }
    }
  }

  private emitEvent(event: string, data: unknown): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(callback => {
        try {
          callback(data);
        } catch (error) {
          console.error(`Error in event listener for ${event}:`, error);
        }
      });
    }
  }

  // Mock implementations - replace with actual gRPC calls
  private async mockSTTCall(request: STTRequest): Promise<STTResult> {
    await new Promise(resolve => setTimeout(resolve, 500));
    return {
      text: "This is a mock transcription of the audio input.",
      confidence: 0.95,
      processingTimeMs: 500,
      language: request.language || 'en-US',
      model: request.model || 'whisper-tiny'
    };
  }

  private async mockTTSCall(request: TTSRequest): Promise<TTSResult> {
    await new Promise(resolve => setTimeout(resolve, 800));
    return {
      audio: {
        data: new ArrayBuffer(1024),
        sampleRate: 22050,
        channels: 1,
        durationMs: request.text.length * 100,
        format: 'wav'
      },
      voiceUsed: request.voiceId || 'default-en',
      processingTimeMs: 800,
      characters: request.text.length,
      durationMs: request.text.length * 100
    };
  }

  private async mockAIVoiceCall(request: AIVoiceRequest): Promise<AIVoiceResult> {
    await new Promise(resolve => setTimeout(resolve, 300));
    return {
      processedText: `Enhanced: ${request.text}`,
      originalText: request.text,
      task: request.task,
      processingTimeMs: 300,
      confidence: 0.88
    };
  }

  private async mockVisionCall(request: VisionRequest): Promise<DetectionResult> {
    await new Promise(resolve => setTimeout(resolve, 600));
    return {
      objects: [
        {
          class: 'person',
          confidence: 0.92,
          bbox: { x: 100, y: 100, width: 200, height: 300 }
        }
      ],
      confidence: 0.92,
      processingTimeMs: 600,
      imageSize: { width: request.image.width, height: request.image.height }
    };
  }

  private async mockMultimodalCall(request: MultimodalRequest): Promise<MultimodalResult> {
    await new Promise(resolve => setTimeout(resolve, 1000));
    return {
      result: { summary: "Multimodal processing completed successfully" },
      confidence: 0.91,
      processingTimeMs: 1000,
      modalities: Object.keys(request).filter(key => request[key as keyof MultimodalRequest] !== undefined),
      insights: [
        {
          type: 'cross_modal',
          description: 'Audio and video content are synchronized',
          confidence: 0.85,
          data: { syncScore: 0.95 }
        }
      ]
    };
  }
}

/**
 * Factory function to create configured client
 */
export function createAudioVideoClient(configs: Record<ServiceType, ServiceClientConfig>): AudioVideoClient {
  return new AudioVideoClient(configs);
}

/**
 * Default configuration for development
 */
export const defaultConfigs: Record<ServiceType, ServiceClientConfig> = {
  stt: {
    endpoint: 'http://localhost:50051',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  tts: {
    endpoint: 'http://localhost:50052',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  'ai-voice': {
    endpoint: 'http://localhost:50053',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  vision: {
    endpoint: 'http://localhost:50054',
    timeout: 30000,
    retries: 3,
    enableLogging: true
  },
  multimodal: {
    endpoint: 'http://localhost:50055',
    timeout: 60000,
    retries: 3,
    enableLogging: true
  }
};
