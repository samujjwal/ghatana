/**
 * @doc.type types
 * @doc.purpose Shared TypeScript interfaces for audio-video application
 * @doc.layer shared
 * @doc.pattern domain-driven design
 */

// Core service types
export type ServiceType = 'stt' | 'tts' | 'ai-voice' | 'vision' | 'multimodal';

// Audio/Video data types
export interface AudioData {
  data: ArrayBuffer;
  sampleRate: number;
  channels: number;
  durationMs: number;
  format: 'wav' | 'mp3' | 'flac' | 'pcm';
}

export interface VideoData {
  data: ArrayBuffer;
  width: number;
  height: number;
  durationMs: number;
  fps: number;
  format: 'mp4' | 'avi' | 'mov';
}

export interface ImageData {
  data: ArrayBuffer;
  width: number;
  height: number;
  format: 'png' | 'jpg' | 'jpeg' | 'webp';
}

// STT (Speech-to-Text) types
export interface STTRequest {
  audio: AudioData;
  language?: string;
  model?: string;
  options?: STTOptions;
}

export interface STTOptions {
  enablePunctuation?: boolean;
  enableTimestamps?: boolean;
  maxAlternatives?: number;
  profanityFilter?: boolean;
}

export interface STTResult {
  text: string;
  confidence: number;
  alternatives?: AlternativeTranscription[];
  words?: WordTimestamp[];
  processingTimeMs: number;
  language: string;
  model: string;
}

export interface AlternativeTranscription {
  text: string;
  confidence: number;
}

export interface WordTimestamp {
  word: string;
  start: number;
  end: number;
  confidence: number;
}

// TTS (Text-to-Speech) types
export interface TTSRequest {
  text: string;
  voiceId?: string;
  language?: string;
  options?: TTSOptions;
}

export interface TTSOptions {
  sampleRate?: number;
  speed?: number;
  pitch?: number;
  volume?: number;
  emotion?: string;
  format?: 'wav' | 'mp3' | 'flac';
}

export interface TTSResult {
  audio: AudioData;
  voiceUsed: string;
  processingTimeMs: number;
  characters: number;
  durationMs: number;
}

// AI Voice types
export interface AIVoiceRequest {
  text: string;
  task: 'enhance' | 'translate' | 'summarize' | 'style';
  options?: AIVoiceOptions;
}

export interface AIVoiceOptions {
  targetLanguage?: string;
  style?: 'formal' | 'casual' | 'professional' | 'creative';
  maxLength?: number;
  preserveTone?: boolean;
}

export interface AIVoiceResult {
  processedText: string;
  originalText: string;
  task: string;
  processingTimeMs: number;
  confidence: number;
}

// Computer Vision types
export interface VisionRequest {
  image: ImageData;
  task: 'detect' | 'classify' | 'segment' | 'analyze';
  options?: VisionOptions;
}

export interface VisionOptions {
  confidenceThreshold?: number;
  maxDetections?: number;
  classes?: string[];
  enableSegmentation?: boolean;
}

export interface DetectionResult {
  objects: DetectedObject[];
  confidence: number;
  processingTimeMs: number;
  imageSize: { width: number; height: number };
}

export interface DetectedObject {
  class: string;
  confidence: number;
  bbox: BoundingBox;
  attributes?: Record<string, any>;
}

export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

// Multimodal types
export interface MultimodalRequest {
  audio?: AudioData;
  video?: VideoData;
  image?: ImageData;
  text?: string;
  task: 'transcribe' | 'synthesize' | 'analyze' | 'translate' | 'summarize';
  options?: MultimodalOptions;
}

export interface MultimodalOptions {
  primaryModality?: 'audio' | 'video' | 'image' | 'text';
  enableCrossModal?: boolean;
  outputFormat?: 'text' | 'audio' | 'video' | 'structured';
  language?: string;
}

export interface MultimodalResult {
  result: any; // Varies based on task and output format
  confidence: number;
  processingTimeMs: number;
  modalities: string[];
  insights?: MultimodalInsight[];
}

export interface MultimodalInsight {
  type: string;
  description: string;
  confidence: number;
  data: any;
}

// Service status types
export interface ServiceStatus {
  service: ServiceType;
  status: 'healthy' | 'degraded' | 'unhealthy';
  uptime: number;
  version: string;
  lastCheck: Date;
  metrics?: ServiceMetrics;
}

export interface ServiceMetrics {
  requestCount: number;
  errorRate: number;
  avgResponseTime: number;
  activeConnections: number;
  memoryUsage?: number;
  cpuUsage?: number;
}

// Application state types
export interface UIState {
  theme: 'light' | 'dark' | 'auto';
  sidebarOpen: boolean;
  activePanel?: string;
  notifications: Notification[];
}

export interface AudioVideoState {
  activeService: ServiceType;
  services: Record<ServiceType, ServiceState>;
  settings: AudioVideoSettings;
  ui: UIState;
}

export interface ServiceState {
  status: ServiceStatus;
  lastResult?: any;
  isProcessing: boolean;
  error?: string;
  configuration: Record<string, any>;
}

export interface AudioVideoSettings {
  services: Record<ServiceType, ServiceSettings>;
  ui: UISettings;
  performance: PerformanceSettings;
  accessibility: AccessibilitySettings;
}

export interface ServiceSettings {
  enabled: boolean;
  endpoint: string;
  timeout: number;
  retries: number;
  customSettings: Record<string, any>;
}

export interface UISettings {
  theme: 'light' | 'dark' | 'auto';
  language: string;
  fontSize: 'small' | 'medium' | 'large';
  layout: 'compact' | 'comfortable' | 'spacious';
  animations: boolean;
  notifications: boolean;
}

export interface PerformanceSettings {
  enableGPU: boolean;
  maxConcurrentRequests: number;
  cacheSize: number;
  enableCompression: boolean;
  bufferSize: number;
}

export interface AccessibilitySettings {
  highContrast: boolean;
  reduceMotion: boolean;
  screenReader: boolean;
  keyboardNavigation: boolean;
  fontSize: number;
  colorBlindMode: 'none' | 'protanopia' | 'deuteranopia' | 'tritanopia';
}

// Workflow types
export interface Workflow {
  id: string;
  name: string;
  description: string;
  steps: WorkflowStep[];
  enabled: boolean;
}

export interface WorkflowStep {
  id: string;
  service: ServiceType;
  operation: string;
  parameters: Record<string, any>;
  conditions?: WorkflowCondition[];
}

export interface WorkflowCondition {
  field: string;
  operator: 'equals' | 'contains' | 'greater' | 'less';
  value: any;
}

export interface WorkflowExecution {
  workflowId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  startTime: Date;
  endTime?: Date;
  results: Record<string, any>;
  error?: string;
}

// Error types
export interface AudioVideoError {
  code: string;
  message: string;
  service?: ServiceType;
  details?: Record<string, any>;
  timestamp: Date;
}

export interface APIError extends AudioVideoError {
  statusCode?: number;
  endpoint?: string;
}

// Event types
export interface AudioVideoEvent {
  type: string;
  service?: ServiceType;
  data: any;
  timestamp: Date;
}

export interface ServiceEvent extends AudioVideoEvent {
  service: ServiceType;
  eventType: 'status_change' | 'result' | 'error' | 'configuration_change';
}

export interface UIEvent extends AudioVideoEvent {
  eventType: 'navigation' | 'settings_change' | 'workflow_start' | 'workflow_complete';
}

// Utility types
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export type ServiceResponse<T = any> = {
  success: boolean;
  data?: T;
  error?: AudioVideoError;
  metadata?: Record<string, any>;
};

export type ProgressCallback = (progress: number, message?: string) => void;
export type ErrorCallback = (error: AudioVideoError) => void;
export type SuccessCallback<T = any> = (result: T) => void;
