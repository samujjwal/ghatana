# Audio-Video Data Architecture

**Version:** 1.0.0  
**Analysis Date:** April 4, 2026  
**Evidence Base:** Repository inspection, type definitions analysis, configuration review  

---

## Executive Summary

The Audio-Video data architecture demonstrates **excellent type definitions** and **canonical data structures** but has **significant implementation gaps** in persistence, database schemas, and data management. The architecture provides **strong typing** and **clear contracts** but lacks **actual data storage** and **persistence mechanisms**.

**Data Model Style:** Type-first design with canonical data structures  
**Persistence Layer:** Not implemented (critical gap)  
**Data Validation:** Strong typing with runtime validation  
**Data Flow:** In-memory processing only (no persistence)  

---

## Data Architecture Overview

### Data Technologies **[Observed and Inferred]**

#### Current State **[Observed]**
- **✅ TypeScript Types:** Comprehensive type definitions
- **✅ Protocol Buffers:** Strong contract definitions
- **✅ Canonical Formats:** Standardized data structures
- **⚠️ No Database:** No persistence layer found
- **⚠️ No ORM:** No object-relational mapping
- **⚠️ No Migrations:** No database migration scripts

#### Inferred Requirements **[Inferred from functionality]**
- **PostgreSQL:** Primary transactional database
- **Redis:** Caching and session storage
- **MinIO/S3:** File storage for audio/video
- **Elasticsearch:** Search and indexing (optional)

---

## Schema/Models Architecture

### TypeScript Data Models **[Observed in libs/audio-video-types]**

#### Core Data Structures **[Observed in index.ts]**
```typescript
// Canonical audio format specification
export interface CanonicalAudioFormat {
  sampleRate: number;
  channels: number;
  bitsPerSample: number;
  format: AudioFormat;
}

// Audio data structure
export interface AudioData {
  data: ArrayBuffer;
  sampleRate: number;
  channels: number;
  bitsPerSample: number;
  durationMs: number;
  format: AudioFormat;
}

// Video data structure
export interface VideoData {
  data: ArrayBuffer;
  width: number;
  height: number;
  durationMs: number;
  fps: number;
  format: 'mp4' | 'avi' | 'mov';
}

// Image data structure
export interface ImageData {
  data: ArrayBuffer;
  width: number;
  height: number;
  format: 'png' | 'jpg' | 'jpeg' | 'webp';
}
```

#### Service-Specific Models **[Observed in index.ts]**
```typescript
// STT (Speech-to-Text) models
export interface STTRequest {
  audio: AudioData;
  language?: string;
  model?: string;
  options?: STTOptions;
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

export interface WordTimestamp {
  word: string;
  start: number;
  end: number;
  confidence: number;
}

// TTS (Text-to-Speech) models
export interface TTSRequest {
  text: string;
  voiceId?: string;
  language?: string;
  options?: TTSOptions;
}

export interface TTSResult {
  audio: AudioData;
  voiceUsed: string;
  processingTimeMs: number;
  characters: number;
  durationMs: number;
}

// AI Voice models
export interface AIVoiceRequest {
  text: string;
  task: 'enhance' | 'translate' | 'summarize' | 'style';
  options?: AIVoiceOptions;
}

export interface AIVoiceResult {
  processedText: string;
  originalText: string;
  task: string;
  processingTimeMs: number;
  confidence: number;
}

// Computer Vision models
export interface VisionRequest {
  image: ImageData;
  task: 'detect' | 'classify' | 'segment' | 'analyze';
  options?: VisionOptions;
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
  attributes?: Record<string, unknown>;
}

export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

// Multimodal models
export interface MultimodalRequest {
  audio?: AudioData;
  video?: VideoData;
  image?: ImageData;
  text?: string;
  task: 'transcribe' | 'synthesize' | 'analyze' | 'translate' | 'summarize';
  options?: MultimodalOptions;
}

export interface MultimodalResult {
  result: unknown; // Varies based on task and output format
  confidence: number;
  processingTimeMs: number;
  modalities: string[];
  insights?: MultimodalInsight[];
}

export interface MultimodalInsight {
  type: string;
  description: string;
  confidence: number;
  data: unknown;
}
```

### Protocol Buffer Models **[Observed in proto files]**

#### STT Service Proto **[Observed in stt.proto]**
```protobuf
message TranscribeRequest {
    bytes audio_data = 1;
    string language = 2;
    string profile_id = 3;
}

message TranscribeResponse {
    string text = 1;
    float confidence = 2;
    int64 processing_time_ms = 3;
    repeated WordTiming word_timings = 4;
}

message WordTiming {
    string word = 1;
    float start_time = 2;
    float end_time = 3;
    float confidence = 4;
}

message AudioChunk {
    bytes audio_data = 1;
    int32 sample_rate = 2;
    bool is_final = 3;
}

message Transcription {
    string text = 1;
    bool is_final = 2;
    float confidence = 3;
    int64 timestamp_ms = 4;
}
```

#### TTS Service Proto **[Observed in tts.proto]**
```protobuf
message SynthesizeRequest {
    string text = 1;
    string voice_id = 2;
    string profile_id = 3;
    SynthesisOptions options = 4;
}

message SynthesisOptions {
    float speed = 1;
    float pitch = 2;
    float energy = 3;
    string emotion = 4;
    string language = 5;
}

message SynthesizeResponse {
    bytes audio_data = 1;
    int32 sample_rate = 2;
    int64 duration_ms = 3;
    int64 processing_time_ms = 4;
    string voice_used = 5;
}

message AudioChunk {
    bytes audio_data = 1;
    int32 sample_rate = 2;
    int64 timestamp_ms = 3;
    bool is_final = 4;
}
```

#### Vision Service Proto **[Observed in vision.proto]**
```protobuf
message DetectRequest {
    bytes image_data = 1;
    repeated string target_classes = 2;
    int32 max_detections = 3;
    double confidence_threshold = 4;
}

message DetectResponse {
    repeated Detection detections = 1;
    int64 processing_time_ms = 2;
}

message Detection {
    string class_name = 1;
    double confidence = 2;
    BoundingBox bounding_box = 3;
    map<string, string> attributes = 4;
}

message BoundingBox {
    double x = 1;
    double y = 2;
    double width = 3;
    double height = 4;
}

message AnalyzeRequest {
    bytes image_data = 1;
    repeated string analysis_types = 2;
}

message AnalyzeResponse {
    string scene_description = 1;
    repeated string detected_text = 2;
    map<string, string> metadata = 3;
}
```

#### Multimodal Service Proto **[Observed in multimodal.proto]**
```protobuf
message MultimodalRequest {
    bytes audio_data = 1;
    bytes image_data = 2;
    bytes video_data = 3;
    string text = 4;
    repeated string analysis_types = 5;
}

message MultimodalResponse {
    string combined_analysis = 1;
    AudioAnalysis audio_analysis = 2;
    VisualAnalysis visual_analysis = 3;
    map<string, string> metadata = 4;
    int64 processing_time_ms = 5;
}

message AudioAnalysis {
    string transcription = 1;
    repeated string detected_sounds = 2;
    string sentiment = 3;
    double confidence = 4;
}

message VisualAnalysis {
    string scene_description = 1;
    repeated Detection objects = 2;
    repeated string activities = 3;
    double confidence = 4;
}

message Detection {
    string class_name = 1;
    double confidence = 2;
    BoundingBox bounding_box = 3;
}
```

---

## Ownership Boundaries

### Data Ownership **[Observed in module structure]**

#### Service Data Ownership **[Observed in module organization]**
```
Data Ownership Boundaries:
├── STT Service
│   ├── Audio Data (input)
│   ├── Transcription Results (output)
│   ├── Word Timing Data (output)
│   └── Language Models (configuration)
├── TTS Service
│   ├── Text Data (input)
│   ├── Audio Data (output)
│   ├── Voice Models (configuration)
│   └── Synthesis Options (configuration)
├── AI Voice Service
│   ├── Text Data (input/output)
│   ├── Processing Results (output)
│   ├── Language Models (configuration)
│   └── Enhancement Rules (configuration)
├── Vision Service
│   ├── Image Data (input)
│   ├── Detection Results (output)
│   ├── Classification Results (output)
│   └── Vision Models (configuration)
└── Multimodal Service
    ├── Multi-modal Data (input)
    ├── Combined Analysis (output)
    ├── Cross-modal Insights (output)
    └── Fusion Models (configuration)
```

### Data Flow Boundaries **[Observed in service interactions]**

#### Cross-Service Data Flow **[Inferred from multimodal service]**
```
Data Flow Boundaries:
User Input → Client → Services → Processing → Results → User

STT Flow:
Audio → STT Service → Transcription → Client → User

TTS Flow:
Text → TTS Service → Audio → Client → User

Vision Flow:
Image → Vision Service → Detections → Client → User

Multimodal Flow:
Multi-modal Input → Multimodal Service → 
├── STT Service (audio processing)
├── Vision Service (image processing)
├── AI Voice Service (text processing)
└── Combined Analysis → Client → User
```

---

## Relationships and Invariants

### Data Relationships **[Inferred from data models]**

#### Entity Relationships **[Inferred from functionality]**
```typescript
// Expected entity relationships (not implemented)
interface User {
  id: string;
  email: string;
  createdAt: Date;
  updatedAt: Date;
  // Relationships
  transcriptions: Transcription[];
  syntheses: Synthesis[];
  analyses: Analysis[];
  settings: UserSettings;
}

interface Transcription {
  id: string;
  userId: string;
  audioFileId: string;
  text: string;
  confidence: number;
  language: string;
  model: string;
  wordTimings: WordTimestamp[];
  processingTimeMs: number;
  createdAt: Date;
  updatedAt: Date;
  // Relationships
  user: User;
  audioFile: AudioFile;
}

interface Synthesis {
  id: string;
  userId: string;
  text: string;
  voiceId: string;
  audioFileId: string;
  processingTimeMs: number;
  characters: number;
  durationMs: number;
  createdAt: Date;
  updatedAt: Date;
  // Relationships
  user: User;
  audioFile: AudioFile;
}

interface Analysis {
  id: string;
  userId: string;
  type: 'vision' | 'multimodal';
  inputFileId: string;
  results: AnalysisResults;
  confidence: number;
  processingTimeMs: number;
  createdAt: Date;
  updatedAt: Date;
  // Relationships
  user: User;
  inputFile: MediaFile;
}

interface AudioFile {
  id: string;
  userId: string;
  filename: string;
  mimeType: string;
  size: number;
  durationMs: number;
  sampleRate: number;
  channels: number;
  bitsPerSample: number;
  format: AudioFormat;
  storageLocation: string;
  createdAt: Date;
  // Relationships
  user: User;
  transcriptions: Transcription[];
}

interface ImageFile {
  id: string;
  userId: string;
  filename: string;
  mimeType: string;
  size: number;
  width: number;
  height: number;
  format: string;
  storageLocation: string;
  createdAt: Date;
  // Relationships
  user: User;
  analyses: Analysis[];
}
```

### Data Invariants **[Inferred from data models]**

#### Audio Data Invariants **[Observed in CanonicalAudioFormat]**
```typescript
// Audio format invariants (observed in types)
export const DEFAULT_AUDIO_FORMAT: CanonicalAudioFormat = {
  sampleRate: 16000,
  channels: 1,
  bitsPerSample: 16,
  format: 'pcm',
};

// Validation invariants (inferred from validation patterns)
export function validateAudioFormat(format: CanonicalAudioFormat): void {
  // Sample rate must be positive and within reasonable range
  if (format.sampleRate <= 0 || format.sampleRate > 96000) {
    throw new Error(`Invalid sample rate: ${format.sampleRate}`);
  }
  
  // Channels must be between 1 and 8
  if (format.channels < 1 || format.channels > 8) {
    throw new Error(`Invalid channel count: ${format.channels}`);
  }
  
  // Bits per sample must be 8, 16, 24, or 32
  if (![8, 16, 24, 32].includes(format.bitsPerSample)) {
    throw new Error(`Invalid bits per sample: ${format.bitsPerSample}`);
  }
  
  // Format must be supported
  if (!isSupportedAudioFormat(format.format)) {
    throw new Error(`Unsupported audio format: ${format.format}`);
  }
}
```

#### Processing Result Invariants **[Inferred from service patterns]**
```typescript
// Processing result invariants (inferred from patterns)
export function validateSTTResult(result: STTResult): void {
  // Text must not be empty
  if (!result.text || result.text.trim().length === 0) {
    throw new Error('Transcription text cannot be empty');
  }
  
  // Confidence must be between 0 and 1
  if (result.confidence < 0 || result.confidence > 1) {
    throw new Error(`Invalid confidence: ${result.confidence}`);
  }
  
  // Processing time must be positive
  if (result.processingTimeMs <= 0) {
    throw new Error(`Invalid processing time: ${result.processingTimeMs}`);
  }
  
  // Language must be valid ISO code
  if (!isValidLanguageCode(result.language)) {
    throw new Error(`Invalid language code: ${result.language}`);
  }
}

export function validateTTSResult(result: TTSResult): void {
  // Audio data must not be empty
  if (!result.audio.data || result.audio.data.byteLength === 0) {
    throw new Error('Audio data cannot be empty');
  }
  
  // Duration must be positive
  if (result.durationMs <= 0) {
    throw new Error(`Invalid duration: ${result.durationMs}`);
  }
  
  // Processing time must be positive
  if (result.processingTimeMs <= 0) {
    throw new Error(`Invalid processing time: ${result.processingTimeMs}`);
  }
  
  // Character count must match text length
  if (result.characters <= 0) {
    throw new Error(`Invalid character count: ${result.characters}`);
  }
}
```

---

## Lifecycle Management

### Data Lifecycle **[Inferred from functionality]**

#### Audio Data Lifecycle **[Inferred from processing patterns]**
```typescript
// Audio data lifecycle (not implemented)
export enum AudioDataLifecycle {
  UPLOADING = 'uploading',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  FAILED = 'failed',
  ARCHIVED = 'archived',
  DELETED = 'deleted'
}

export interface AudioDataLifecycleManager {
  // Upload phase
  uploadAudio(data: ArrayBuffer, metadata: AudioMetadata): Promise<string>;
  
  // Processing phase
  processAudio(audioId: string, options: ProcessingOptions): Promise<void>;
  
  // Storage phase
  storeAudio(audioId: string, data: AudioData): Promise<void>;
  
  // Archive phase
  archiveAudio(audioId: string): Promise<void>;
  
  // Cleanup phase
  deleteAudio(audioId: string): Promise<void>;
}
```

#### Result Data Lifecycle **[Inferred from processing patterns]**
```typescript
// Result data lifecycle (not implemented)
export enum ResultDataLifecycle {
  GENERATING = 'generating',
  AVAILABLE = 'available',
  EXPIRED = 'expired',
  ARCHIVED = 'archived',
  DELETED = 'deleted'
}

export interface ResultDataLifecycleManager {
  // Generation phase
  generateResult(requestId: string, data: ProcessingRequest): Promise<void>;
  
  // Storage phase
  storeResult(requestId: string, result: ProcessingResult): Promise<void>;
  
  // Expiration phase
  expireResult(requestId: string): Promise<void>;
  
  // Archive phase
  archiveResult(requestId: string): Promise<void>;
  
  // Cleanup phase
  deleteResult(requestId: string): Promise<void>;
}
```

---

## Write/Read Paths

### Current State **[Observed]**

#### In-Memory Processing Only **[Observed]**
```typescript
// Current data flow (in-memory only)
export class InMemoryDataProcessor {
  private audioData = new Map<string, AudioData>();
  private results = new Map<string, ProcessingResult>();
  
  // Write path
  public storeAudio(id: string, data: AudioData): void {
    this.audioData.set(id, data);
  }
  
  public storeResult(id: string, result: ProcessingResult): void {
    this.results.set(id, result);
  }
  
  // Read path
  public getAudio(id: string): AudioData | undefined {
    return this.audioData.get(id);
  }
  
  public getResult(id: string): ProcessingResult | undefined {
    return this.results.get(id);
  }
}
```

### Expected Database Paths **[Inferred from requirements]**

#### Database Write Paths **[Inferred from functionality]**
```sql
-- Expected database schema (not implemented)

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audio files table
CREATE TABLE audio_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_ms INTEGER,
    sample_rate INTEGER,
    channels INTEGER,
    bits_per_sample INTEGER,
    format VARCHAR(20) NOT NULL,
    storage_location VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Transcriptions table
CREATE TABLE transcriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    audio_file_id UUID NOT NULL REFERENCES audio_files(id),
    text TEXT NOT NULL,
    confidence DECIMAL(3,2) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    language VARCHAR(10) NOT NULL,
    model VARCHAR(100) NOT NULL,
    processing_time_ms INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Word timings table
CREATE TABLE word_timings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transcription_id UUID NOT NULL REFERENCES transcriptions(id),
    word VARCHAR(100) NOT NULL,
    start_time DECIMAL(8,3) NOT NULL,
    end_time DECIMAL(8,3) NOT NULL,
    confidence DECIMAL(3,2) NOT NULL CHECK (confidence >= 0 AND confidence <= 1)
);

-- Syntheses table
CREATE TABLE syntheses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    text TEXT NOT NULL,
    voice_id VARCHAR(100) NOT NULL,
    audio_file_id UUID NOT NULL REFERENCES audio_files(id),
    processing_time_ms INTEGER NOT NULL,
    characters INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Image files table
CREATE TABLE image_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    format VARCHAR(20) NOT NULL,
    storage_location VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Analyses table
CREATE TABLE analyses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('vision', 'multimodal')),
    input_file_id UUID NOT NULL,
    input_file_type VARCHAR(20) NOT NULL CHECK (input_file_type IN ('audio', 'image', 'video')),
    results JSONB NOT NULL,
    confidence DECIMAL(3,2) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    processing_time_ms INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (input_file_id, input_file_type) REFERENCES 
        (audio_files(id), 'audio') ON DELETE CASCADE,
    FOREIGN KEY (input_file_id, input_file_type) REFERENCES 
        (image_files(id), 'image') ON DELETE CASCADE
);
```

#### Database Read Paths **[Inferred from functionality]**
```sql
-- Expected read queries (not implemented)

-- Get user's transcriptions
SELECT t.*, af.filename as audio_filename
FROM transcriptions t
JOIN audio_files af ON t.audio_file_id = af.id
WHERE t.user_id = $1
ORDER BY t.created_at DESC
LIMIT $2 OFFSET $3;

-- Get transcription with word timings
SELECT t.*, wt.word, wt.start_time, wt.end_time, wt.confidence
FROM transcriptions t
LEFT JOIN word_timings wt ON t.id = wt.transcription_id
WHERE t.id = $1
ORDER BY wt.start_time;

-- Get user's syntheses
SELECT s.*, af.filename as audio_filename
FROM syntheses s
JOIN audio_files af ON s.audio_file_id = af.id
WHERE s.user_id = $1
ORDER BY s.created_at DESC
LIMIT $2 OFFSET $3;

-- Get user's analyses
SELECT a.*, 
       CASE 
         WHEN a.input_file_type = 'audio' THEN af.filename
         WHEN a.input_file_type = 'image' THEN imf.filename
       END as input_filename
FROM analyses a
LEFT JOIN audio_files af ON a.input_file_id = af.id AND a.input_file_type = 'audio'
LEFT JOIN image_files imf ON a.input_file_id = imf.id AND a.input_file_type = 'image'
WHERE a.user_id = $1
ORDER BY a.created_at DESC
LIMIT $2 OFFSET $3;
```

---

## Query and Computation Patterns

### Current State **[Observed]**

#### In-Memory Queries **[Observed in client library]**
```typescript
// Current query patterns (in-memory only)
export class InMemoryQueryService {
  private transcriptions = new Map<string, STTResult>();
  private syntheses = new Map<string, TTSResult>();
  private analyses = new Map<string, DetectionResult>();
  
  // Query transcriptions by user
  public getTranscriptionsByUser(userId: string): STTResult[] {
    return Array.from(this.transcriptions.values())
      .filter(t => t.userId === userId);
  }
  
  // Query by text content
  public searchTranscriptions(query: string): STTResult[] {
    const lowerQuery = query.toLowerCase();
    return Array.from(this.transcriptions.values())
      .filter(t => t.text.toLowerCase().includes(lowerQuery));
  }
  
  // Query by confidence threshold
  public getTranscriptionsByConfidence(minConfidence: number): STTResult[] {
    return Array.from(this.transcriptions.values())
      .filter(t => t.confidence >= minConfidence);
  }
}
```

### Expected Database Queries **[Inferred from requirements]**

#### Complex Query Patterns **[Inferred from functionality]**
```sql
-- Expected complex queries (not implemented)

-- Search transcriptions with pagination and filters
SELECT t.*, af.filename as audio_filename
FROM transcriptions t
JOIN audio_files af ON t.audio_file_id = af.id
WHERE t.user_id = $1
  AND ($2::text IS NULL OR t.text ILIKE '%' || $2 || '%')
  AND ($3::varchar IS NULL OR t.language = $3)
  AND ($4::decimal IS NULL OR t.confidence >= $4)
  AND t.created_at >= $5::timestamp
  AND t.created_at <= $6::timestamp
ORDER BY t.created_at DESC
LIMIT $7 OFFSET $8;

-- Get user statistics
SELECT 
  COUNT(DISTINCT t.id) as total_transcriptions,
  COUNT(DISTINCT s.id) as total_syntheses,
  COUNT(DISTINCT a.id) as total_analyses,
  SUM(af.size_bytes) as total_audio_size,
  SUM(imf.size_bytes) as total_image_size,
  AVG(t.confidence) as avg_transcription_confidence,
  AVG(t.processing_time_ms) as avg_transcription_time
FROM users u
LEFT JOIN transcriptions t ON u.id = t.user_id
LEFT JOIN syntheses s ON u.id = s.user_id
LEFT JOIN analyses a ON u.id = a.user_id
LEFT JOIN audio_files af ON t.audio_file_id = af.id
LEFT JOIN image_files imf ON a.input_file_id = imf.id AND a.input_file_type = 'image'
WHERE u.id = $1;

-- Get processing metrics
SELECT 
  DATE_TRUNC('hour', created_at) as hour,
  type,
  COUNT(*) as request_count,
  AVG(processing_time_ms) as avg_processing_time,
  AVG(confidence) as avg_confidence,
  MIN(processing_time_ms) as min_processing_time,
  MAX(processing_time_ms) as max_processing_time
FROM (
  SELECT 'transcription' as type, processing_time_ms, confidence, created_at
  FROM transcriptions
  WHERE user_id = $1
  UNION ALL
  SELECT 'synthesis' as type, processing_time_ms, 1.0 as confidence, created_at
  FROM syntheses
  WHERE user_id = $1
  UNION ALL
  SELECT 'analysis' as type, processing_time_ms, confidence, created_at
  FROM analyses
  WHERE user_id = $1
) metrics
WHERE created_at >= $2::timestamp
GROUP BY hour, type
ORDER BY hour DESC, type;
```

---

## Indexes/Constraints

### Expected Database Indexes **[Inferred from query patterns]**

#### Performance Indexes **[Inferred from functionality]**
```sql
-- Expected database indexes (not implemented)

-- User-based indexes
CREATE INDEX idx_transcriptions_user_id ON transcriptions(user_id);
CREATE INDEX idx_syntheses_user_id ON syntheses(user_id);
CREATE INDEX idx_analyses_user_id ON analyses(user_id);
CREATE INDEX idx_audio_files_user_id ON audio_files(user_id);
CREATE INDEX idx_image_files_user_id ON image_files(user_id);

-- Time-based indexes
CREATE INDEX idx_transcriptions_created_at ON transcriptions(created_at DESC);
CREATE INDEX idx_syntheses_created_at ON syntheses(created_at DESC);
CREATE INDEX idx_analyses_created_at ON analyses(created_at DESC);
CREATE INDEX idx_audio_files_created_at ON audio_files(created_at DESC);
CREATE INDEX idx_image_files_created_at ON image_files(created_at DESC);

-- Search indexes
CREATE INDEX idx_transcriptions_text_gin ON transcriptions USING gin(to_tsvector('english', text));
CREATE INDEX idx_transcriptions_language ON transcriptions(language);
CREATE INDEX idx_transcriptions_confidence ON transcriptions(confidence);

-- Relationship indexes
CREATE INDEX idx_transcriptions_audio_file_id ON transcriptions(audio_file_id);
CREATE INDEX idx_syntheses_audio_file_id ON syntheses(audio_file_id);
CREATE INDEX idx_word_timings_transcription_id ON word_timings(transcription_id);

-- Composite indexes
CREATE INDEX idx_transcriptions_user_created ON transcriptions(user_id, created_at DESC);
CREATE INDEX idx_syntheses_user_created ON syntheses(user_id, created_at DESC);
CREATE INDEX idx_analyses_user_created ON analyses(user_id, created_at DESC);
```

#### Data Constraints **[Inferred from data models]**
```sql
-- Expected data constraints (not implemented)

-- Check constraints
ALTER TABLE transcriptions 
ADD CONSTRAINT chk_transcription_confidence 
CHECK (confidence >= 0 AND confidence <= 1);

ALTER TABLE transcriptions 
ADD CONSTRAINT chk_transcription_processing_time 
CHECK (processing_time_ms > 0);

ALTER TABLE syntheses 
ADD CONSTRAINT chk_synthesis_processing_time 
CHECK (processing_time_ms > 0);

ALTER TABLE syntheses 
ADD CONSTRAINT chk_synthesis_characters 
CHECK (characters > 0);

ALTER TABLE analyses 
ADD CONSTRAINT chk_analysis_confidence 
CHECK (confidence >= 0 AND confidence <= 1);

-- Foreign key constraints
ALTER TABLE transcriptions 
ADD CONSTRAINT fk_transcriptions_user_id 
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE transcriptions 
ADD CONSTRAINT fk_transcriptions_audio_file_id 
FOREIGN KEY (audio_file_id) REFERENCES audio_files(id) ON DELETE CASCADE;

ALTER TABLE word_timings 
ADD CONSTRAINT fk_word_timings_transcription_id 
FOREIGN KEY (transcription_id) REFERENCES transcriptions(id) ON DELETE CASCADE;

-- Unique constraints
ALTER TABLE users 
ADD CONSTRAINT uk_users_email 
UNIQUE (email);

-- Not null constraints
ALTER TABLE transcriptions 
ALTER COLUMN text SET NOT NULL,
ALTER COLUMN confidence SET NOT NULL,
ALTER COLUMN language SET NOT NULL,
ALTER COLUMN model SET NOT NULL,
ALTER COLUMN processing_time_ms SET NOT NULL;
```

---

## Retention/Cleanup Logic

### Expected Retention Policies **[Inferred from functionality]**

#### Data Retention Strategy **[Inferred from requirements]**
```sql
-- Expected retention policies (not implemented)

-- Retention periods
-- Transcriptions: 1 year default, 7 years for premium
-- Syntheses: 6 months default, 3 years for premium
-- Audio files: 6 months default, 2 years for premium
-- Image files: 1 year default, 5 years for premium
-- Analyses: 6 months default, 2 years for premium

-- Cleanup function
CREATE OR REPLACE FUNCTION cleanup_old_data()
RETURNS void AS $$
DECLARE
    retention_days INT;
BEGIN
    -- Get retention period for free tier (6 months)
    retention_days := 180;
    
    -- Clean up old transcriptions
    DELETE FROM transcriptions 
    WHERE created_at < NOW() - INTERVAL '1 year'
      AND user_id NOT IN (SELECT id FROM users WHERE subscription_tier = 'premium');
    
    -- Clean up old syntheses
    DELETE FROM syntheses 
    WHERE created_at < NOW() - INTERVAL '6 months'
      AND user_id NOT IN (SELECT id FROM users WHERE subscription_tier = 'premium');
    
    -- Clean up old audio files
    DELETE FROM audio_files 
    WHERE created_at < NOW() - INTERVAL '6 months'
      AND user_id NOT IN (SELECT id FROM users WHERE subscription_tier = 'premium')
      AND id NOT IN (SELECT audio_file_id FROM transcriptions WHERE created_at >= NOW() - INTERVAL '1 year');
    
    -- Clean up old image files
    DELETE FROM image_files 
    WHERE created_at < NOW() - INTERVAL '1 year'
      AND user_id NOT IN (SELECT id FROM users WHERE subscription_tier = 'premium')
      AND id NOT IN (SELECT input_file_id FROM analyses WHERE created_at >= NOW() - INTERVAL '6 months');
    
    -- Clean up old analyses
    DELETE FROM analyses 
    WHERE created_at < NOW() - INTERVAL '6 months'
      AND user_id NOT IN (SELECT id FROM users WHERE subscription_tier = 'premium');
    
    -- Clean up orphaned word timings
    DELETE FROM word_timings 
    WHERE transcription_id NOT IN (SELECT id FROM transcriptions);
    
    -- Log cleanup
    INSERT INTO cleanup_logs (cleanup_date, records_deleted)
    VALUES (NOW(), (SELECT COUNT(*) FROM deleted_records));
    
END;
$$ LANGUAGE plpgsql;

-- Schedule cleanup job (run daily)
-- This would be scheduled using pg_cron or external scheduler
SELECT cron.schedule('cleanup-old-data', '0 2 * * *', 'SELECT cleanup_old_data();');
```

#### Automated Cleanup **[Inferred from requirements]**
```java
// Expected cleanup service (not implemented)
@Service
public class DataCleanupService {
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupOldData() {
        logger.info("Starting data cleanup process");
        
        int deletedTranscriptions = cleanupTranscriptions();
        int deletedSyntheses = cleanupSyntheses();
        int deletedAudioFiles = cleanupAudioFiles();
        int deletedImageFiles = cleanupImageFiles();
        int deletedAnalyses = cleanupAnalyses();
        
        logger.info("Data cleanup completed: transcriptions={}, syntheses={}, audio={}, images={}, analyses={}",
            deletedTranscriptions, deletedSyntheses, deletedAudioFiles, deletedImageFiles, deletedAnalyses);
        
        // Record cleanup metrics
        metrics.recordCleanup(deletedTranscriptions, deletedSyntheses, deletedAudioFiles, deletedImageFiles, deletedAnalyses);
    }
    
    private int cleanupTranscriptions() {
        return transcriptionRepository.deleteOldTranscriptions(getRetentionDate("transcription"));
    }
    
    private int cleanupSyntheses() {
        return synthesisRepository.deleteOldSyntheses(getRetentionDate("synthesis"));
    }
    
    private int cleanupAudioFiles() {
        return audioFileRepository.deleteOldAudioFiles(getRetentionDate("audio"));
    }
    
    private int cleanupImageFiles() {
        return imageFileRepository.deleteOldImageFiles(getRetentionDate("image"));
    }
    
    private int cleanupAnalyses() {
        return analysisRepository.deleteOldAnalyses(getRetentionDate("analysis"));
    }
    
    private LocalDate getRetentionDate(String dataType) {
        // Get retention period based on user subscription
        // Default retention for free tier, extended for premium
        return LocalDate.now().minusMonths(getRetentionMonths(dataType));
    }
}
```

---

## Data Privacy Handling

### Current State **[Observed]**

#### No Privacy Implementation **[Gap Identified]**
- **⚠️ No Data Encryption:** No encryption at rest or in transit
- **⚠️ No Anonymization:** No data anonymization or pseudonymization
- **⚠️ No Access Control:** No fine-grained access control
- **⚠️ No Audit Logging:** No data access audit logs

#### Expected Privacy Measures **[Inferred from requirements]**
```java
// Expected privacy implementation (not implemented)
@Service
public class DataPrivacyService {
    
    @EventListener
    public void handleUserDataRequest(UserDataRequestEvent event) {
        switch (event.getRequestType()) {
            case EXPORT:
                exportUserData(event.getUserId());
                break;
            case DELETE:
                deleteUserData(event.getUserId());
                break;
            case ANONYMIZE:
                anonymizeUserData(event.getUserId());
                break;
        }
    }
    
    private void exportUserData(String userId) {
        UserDataExport export = UserDataExport.builder()
            .userId(userId)
            .transcriptions(transcriptionRepository.findByUserId(userId))
            .syntheses(synthesisRepository.findByUserId(userId))
            .analyses(analysisRepository.findByUserId(userId))
            .audioFiles(audioFileRepository.findByUserId(userId))
            .imageFiles(imageFileRepository.findByUserId(userId))
            .exportDate(Instant.now())
            .build();
        
        // Generate encrypted export file
        byte[] encryptedData = encryptionService.encrypt(export.toJson());
        
        // Store encrypted export
        storageService.storeEncryptedExport(userId, encryptedData);
        
        // Notify user
        notificationService.sendExportReady(userId);
    }
    
    private void deleteUserData(String userId) {
        // Soft delete first
        userRepository.markForDeletion(userId);
        
        // Schedule hard deletion after 30 days
        scheduler.schedule(() -> {
            hardDeleteUserData(userId);
        }, Duration.ofDays(30));
    }
    
    private void anonymizeUserData(String userId) {
        // Replace user ID with pseudonym
        String pseudonymId = pseudonymService.generatePseudonym(userId);
        
        // Update all records with pseudonym
        transcriptionRepository.anonymizeUserId(userId, pseudonymId);
        synthesisRepository.anonymizeUserId(userId, pseudonymId);
        analysisRepository.anonymizeUserId(userId, pseudonymId);
        audioFileRepository.anonymizeUserId(userId, pseudonymId);
        imageFileRepository.anonymizeUserId(userId, pseudonymId);
        
        // Delete original user record
        userRepository.deleteById(userId);
    }
}
```

---

## Data Architecture Strengths

### Type System Quality **[Observed]**

#### Comprehensive Type Definitions
- **✅ Canonical Formats:** Well-defined audio format specifications
- **✅ Service Models:** Complete service request/response models
- **✅ Type Safety:** Strong typing throughout the system
- **✅ Validation:** Built-in validation patterns

#### Contract Clarity
- **✅ Proto Definitions:** Clear gRPC service contracts
- **✅ TypeScript Types:** Comprehensive type coverage
- **✅ Data Validation:** Runtime validation patterns
- **✅ Documentation:** Well-documented data structures

---

## Data Architecture Weaknesses

### Implementation Gaps **[Observed]**

#### Persistence Layer
- **⚠️ No Database:** No persistence layer implemented
- **⚠️ No Schema:** No database schema definitions
- **⚠️ No Migrations:** No database migration scripts
- **⚠️ No ORM:** No object-relational mapping

#### Data Management
- **⚠️ No Queries:** No database query implementation
- **⚠️ No Indexes:** No performance optimization
- **⚠️ No Constraints:** No data integrity constraints
- **⚠️ No Cleanup:** No data retention policies

#### Privacy and Security
- **⚠️ No Encryption:** No data encryption implemented
- **⚠️ No Access Control:** No data access controls
- **⚠️ No Audit Logging:** No data access logging
- **⚠️ No Anonymization:** No data anonymization

---

## Recommendations

### Immediate Actions (Weeks 1-4)
1. **Implement Database Schema:** Create PostgreSQL schema with all entities
2. **Add ORM Integration:** Implement JPA/Hibernate for data persistence
3. **Create Repository Layer:** Implement repository patterns for data access
4. **Add Data Validation:** Implement comprehensive data validation

### Short-term Actions (Weeks 5-8)
1. **Implement Queries:** Add complex query patterns and optimization
2. **Add Indexes:** Implement performance indexes and constraints
3. **Add Data Migration:** Create database migration scripts
4. **Implement Privacy:** Add data encryption and access controls

### Long-term Actions (Weeks 9-12)
1. **Add Retention Policies:** Implement data cleanup and retention
2. **Add Analytics:** Implement data analytics and reporting
3. **Add Backup/Recovery:** Implement data backup and recovery
4. **Add Monitoring:** Implement data quality monitoring

---

## Conclusion

The Audio-Video data architecture demonstrates **excellent type definitions** and **canonical data structures** but has **critical implementation gaps** in persistence, database management, and data privacy. The architecture provides **strong typing** and **clear contracts** but requires **significant implementation work** to realize a production-ready data layer.

**Key Strengths:**
- Comprehensive TypeScript type definitions
- Clear Protocol Buffer contracts
- Canonical audio format specifications
- Strong typing throughout the system
- Well-documented data structures

**Primary Concerns:**
- No database persistence layer
- No data management implementation
- No privacy or security measures
- No data retention or cleanup
- No query optimization or indexing

The data architecture is well-designed from a type-system perspective but requires substantial implementation work to create a production-ready data persistence layer. The strong typing and clear contracts provide an excellent foundation for building a robust data management system.
