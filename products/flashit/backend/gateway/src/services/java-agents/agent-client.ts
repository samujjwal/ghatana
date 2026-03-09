/**
 * Java Agent Service HTTP Client
 * Provides HTTP client for communicating with the Java Agent Service
 *
 * @doc.type service
 * @doc.purpose HTTP client for Java agent service integration
 * @doc.layer infrastructure
 * @doc.pattern HTTPClient
 */

import { fetch } from 'undici';
import { getCircuitBreaker } from '../../lib/circuit-breaker';

// Configuration
const AGENT_SERVICE_URL = process.env.JAVA_AGENT_SERVICE_URL || 'http://localhost:8090';
const AGENT_SERVICE_TIMEOUT = parseInt(process.env.JAVA_AGENT_SERVICE_TIMEOUT || '30000');

// Request/Response interfaces matching Java DTOs

export interface ClassificationRequest {
  content: string;
  transcript?: string;
  contentType: string;
  emotions: string[];
  tags: string[];
  userIntent?: string;
  availableSpheres: SphereInfo[];
  userId: string;
}

export interface SphereInfo {
  id: string;
  name: string;
  description?: string;
  type: string;
}

export interface ClassificationResponse {
  sphereId: string;
  sphereName: string;
  confidence: number;
  reasoning: string;
  alternatives: SphereSuggestion[];
  processingTimeMs: number;
  model: string;
}

export interface SphereSuggestion {
  sphereId: string;
  sphereName: string;
  confidence: number;
  reasoning: string;
}

export interface EmbeddingRequest {
  momentId?: string;
  text: string;
  userId: string;
  contentType: string;
  store: boolean;
}

export interface EmbeddingResponse {
  momentId?: string;
  embedding: number[];
  dimensions: number;
  tokenCount: number;
  model: string;
  processingTimeMs: number;
  stored: boolean;
}

export interface SemanticSearchRequest {
  query: string;
  userId: string;
  sphereIds: string[];
  limit: number;
  similarityThreshold: number;
}

export interface SemanticSearchResponse {
  results: SearchResult[];
  totalResults: number;
  query: string;
  processingTimeMs: number;
}

export interface SearchResult {
  momentId: string;
  content: string;
  sphereId: string;
  similarity: number;
}

export interface ReflectionRequest {
  moments: MomentData[];
  userId: string;
  sphereId: string;
  reflectionType: 'insights' | 'patterns' | 'connections';
  timeRange?: string;
}

export interface MomentData {
  id: string;
  content: string;
  transcript?: string;
  capturedAt: string;
  emotions: string[];
  tags: string[];
}

export interface ReflectionResponse {
  summary: string;
  insights: string[];
  patterns: PatternInfo[];
  connections: ConnectionInfo[];
  themes: string[];
  actionItems: string[];
  processingTimeMs: number;
  model: string;
}

export interface PatternInfo {
  pattern: string;
  frequency: number;
  confidence: number;
  examples: string[];
}

export interface ConnectionInfo {
  momentId: string;
  relationship: string;
  confidence: number;
}

export interface NLPRequest {
  momentId?: string;
  text: string;
  userId: string;
  analysisTypes: string[];
}

export interface NLPResponse {
  momentId?: string;
  entities: Entity[];
  sentiment?: SentimentResult;
  mood?: MoodResult;
  processingTimeMs: number;
  model: string;
}

export interface Entity {
  text: string;
  type: string;
  confidence: number;
  startOffset: number;
  endOffset: number;
}

export interface SentimentResult {
  label: string;
  score: number;
  positive: number;
  negative: number;
  neutral: number;
}

export interface MoodResult {
  primaryMood: string;
  confidence: number;
  secondaryMoods: string[];
  intensity: number;
}

export interface TranscriptionRequest {
  momentId: string;
  audioUrl?: string;
  audioData?: string;
  language?: string;
  userId: string;
}

export interface TranscriptionResponse {
  momentId: string;
  jobId: string;
  status: string;
  transcript?: string;
  language?: string;
  confidence?: number;
  segments?: TranscriptionSegment[];
  processingTimeMs: number;
  model: string;
}

export interface TranscriptionSegment {
  start: number;
  end: number;
  text: string;
  confidence: number;
}

// =========================================================================
// Recommendation Agent Types
// =========================================================================

export interface RecommendationRequest {
  userId: string;
  recentMoments: MomentData[];
  sphereIds?: string[];
  strategies?: string[];
  limit: number;
  excludeIds?: string[];
}

export interface RecommendationItem {
  type: string;
  strategy: string;
  title: string;
  content: string;
  score: number;
  reasoning: string;
  relatedMomentIds: string[];
  actionUrl?: string;
}

export interface RecommendationResponse {
  recommendations: RecommendationItem[];
  totalGenerated: number;
  strategies: string[];
  processingTimeMs: number;
  model: string;
}

// =========================================================================
// Knowledge Graph Agent Types
// =========================================================================

export interface KnowledgeGraphRequest {
  userId: string;
  moments?: MomentData[];
  operation: 'extract' | 'query' | 'expand';
  queryNode?: string;
  depth?: number;
  limit?: number;
}

export interface GraphNode {
  id: string;
  name: string;
  nodeType: string;
  entityType?: string;
  weight: number;
  momentCount: number;
  relatedNodeIds: string[];
}

export interface GraphEdgeInfo {
  sourceId: string;
  sourceType: string;
  targetId: string;
  targetType: string;
  edgeType: string;
  weight: number;
  occurrences: number;
}

export interface KnowledgeGraphResponse {
  nodes: GraphNode[];
  edges: GraphEdgeInfo[];
  totalNodes: number;
  totalEdges: number;
  processingTimeMs: number;
  model: string;
}

// =========================================================================
// Intelligence Accumulation Agent Types
// =========================================================================

export interface IntelligenceAccumulationRequest {
  userId: string;
  moments: MomentData[];
  existingTopics?: string[];
  existingEntities?: string[];
  profileVersion: number;
}

export interface TopicWeight {
  topic: string;
  weight: number;
  momentCount: number;
  trend: string;
}

export interface EntityWeight {
  entity: string;
  entityType: string;
  weight: number;
  mentionCount: number;
}

export interface IntelligenceAccumulationResponse {
  userId: string;
  topTopics: TopicWeight[];
  topEntities: EntityWeight[];
  emotionProfile: Record<string, number>;
  activityPattern: Record<string, unknown>;
  newInsights: string[];
  profileVersion: number;
  processingTimeMs: number;
  model: string;
}

export interface AgentInfo {
  id: string;
  name: string;
  description: string;
  status: string;
  capabilities: string[];
}

export interface HealthResponse {
  status: string;
  timestamp: string;
  service: string;
}

/**
 * Java Agent Service Client
 */
export class JavaAgentClient {
  private baseUrl: string;
  private timeout: number;
  private circuitBreaker: ReturnType<typeof getCircuitBreaker>;

  constructor(baseUrl?: string, timeout?: number) {
    this.baseUrl = baseUrl || AGENT_SERVICE_URL;
    this.timeout = timeout || AGENT_SERVICE_TIMEOUT;
    this.circuitBreaker = getCircuitBreaker('java-agent-service', {
      failureThreshold: 3,
      successThreshold: 2,
      timeout: this.timeout,
      resetTimeout: 60000, // 1 minute
    });
  }

  /**
   * Check if the Java agent service is healthy
   */
  async isHealthy(): Promise<boolean> {
    try {
      const response = await this.get<HealthResponse>('/health');
      return response.status === 'healthy';
    } catch {
      return false;
    }
  }

  /**
   * Check if the Java agent service is ready
   */
  async isReady(): Promise<{ ready: boolean; agents: string[]; openAiConfigured: boolean }> {
    try {
      const response = await this.get<{ ready: boolean; agents: string[]; openAiConfigured: boolean }>('/ready');
      return response;
    } catch {
      return { ready: false, agents: [], openAiConfigured: false };
    }
  }

  /**
   * List all available agents
   */
  async listAgents(): Promise<AgentInfo[]> {
    const response = await this.get<{ agents: AgentInfo[] }>('/api/v1/agents');
    return response.agents;
  }

  /**
   * Get agent status
   */
  async getAgentStatus(agentId: string): Promise<AgentInfo> {
    return this.get<AgentInfo>(`/api/v1/agents/${agentId}/status`);
  }

  // =========================================================================
  // Classification Agent
  // =========================================================================

  /**
   * Classify a moment into a sphere
   */
  async classify(request: ClassificationRequest): Promise<ClassificationResponse> {
    return this.post<ClassificationResponse>('/api/v1/agents/classification/classify', request);
  }

  /**
   * Get sphere suggestions for a moment
   */
  async suggestSpheres(request: ClassificationRequest): Promise<SphereSuggestion[]> {
    return this.post<SphereSuggestion[]>('/api/v1/agents/classification/suggest-spheres', request);
  }

  // =========================================================================
  // Embedding Agent
  // =========================================================================

  /**
   * Generate embedding for text
   */
  async generateEmbedding(request: EmbeddingRequest): Promise<EmbeddingResponse> {
    return this.post<EmbeddingResponse>('/api/v1/agents/embedding/generate', request);
  }

  /**
   * Generate embeddings for multiple texts
   */
  async generateBatchEmbeddings(requests: EmbeddingRequest[]): Promise<EmbeddingResponse[]> {
    return this.post<EmbeddingResponse[]>('/api/v1/agents/embedding/batch', requests);
  }

  /**
   * Perform semantic search
   */
  async semanticSearch(request: SemanticSearchRequest): Promise<SemanticSearchResponse> {
    return this.post<SemanticSearchResponse>('/api/v1/agents/embedding/search', request);
  }

  // =========================================================================
  // Reflection Agent
  // =========================================================================

  /**
   * Generate insights from moments
   */
  async generateInsights(request: ReflectionRequest): Promise<ReflectionResponse> {
    return this.post<ReflectionResponse>('/api/v1/agents/reflection/insights', request);
  }

  /**
   * Detect patterns across moments
   */
  async detectPatterns(request: ReflectionRequest): Promise<ReflectionResponse> {
    return this.post<ReflectionResponse>('/api/v1/agents/reflection/patterns', request);
  }

  /**
   * Find connections between moments
   */
  async findConnections(request: ReflectionRequest): Promise<ReflectionResponse> {
    return this.post<ReflectionResponse>('/api/v1/agents/reflection/connections', request);
  }

  // =========================================================================
  // Transcription Agent
  // =========================================================================

  /**
   * Transcribe audio
   */
  async transcribe(request: TranscriptionRequest): Promise<TranscriptionResponse> {
    return this.post<TranscriptionResponse>('/api/v1/agents/transcription/transcribe', request);
  }

  /**
   * Get transcription job status
   */
  async getTranscriptionStatus(jobId: string): Promise<TranscriptionResponse> {
    return this.get<TranscriptionResponse>(`/api/v1/agents/transcription/status/${jobId}`);
  }

  // =========================================================================
  // NLP Agent
  // =========================================================================

  /**
   * Extract entities from text
   */
  async extractEntities(request: NLPRequest): Promise<NLPResponse> {
    return this.post<NLPResponse>('/api/v1/agents/nlp/extract-entities', request);
  }

  /**
   * Analyze sentiment
   */
  async analyzeSentiment(request: NLPRequest): Promise<NLPResponse> {
    return this.post<NLPResponse>('/api/v1/agents/nlp/analyze-sentiment', request);
  }

  /**
   * Detect mood
   */
  async detectMood(request: NLPRequest): Promise<NLPResponse> {
    return this.post<NLPResponse>('/api/v1/agents/nlp/detect-mood', request);
  }

  // =========================================================================
  // Recommendation Agent
  // =========================================================================

  /**
   * Generate personalized recommendations
   */
  async generateRecommendations(request: RecommendationRequest): Promise<RecommendationResponse> {
    return this.post<RecommendationResponse>('/api/v1/agents/recommendation/generate', request);
  }

  // =========================================================================
  // Knowledge Graph Agent
  // =========================================================================

  /**
   * Extract knowledge graph from moments
   */
  async extractGraph(request: KnowledgeGraphRequest): Promise<KnowledgeGraphResponse> {
    return this.post<KnowledgeGraphResponse>('/api/v1/agents/knowledge-graph/extract', request);
  }

  /**
   * Query knowledge graph from a starting node
   */
  async queryGraph(request: KnowledgeGraphRequest): Promise<KnowledgeGraphResponse> {
    return this.post<KnowledgeGraphResponse>('/api/v1/agents/knowledge-graph/query', request);
  }

  /**
   * Expand knowledge graph with new connections
   */
  async expandGraph(request: KnowledgeGraphRequest): Promise<KnowledgeGraphResponse> {
    return this.post<KnowledgeGraphResponse>('/api/v1/agents/knowledge-graph/expand', request);
  }

  // =========================================================================
  // Intelligence Accumulation Agent
  // =========================================================================

  /**
   * Compute or update user knowledge profile
   */
  async computeProfile(request: IntelligenceAccumulationRequest): Promise<IntelligenceAccumulationResponse> {
    return this.post<IntelligenceAccumulationResponse>('/api/v1/agents/intelligence/compute-profile', request);
  }

  // =========================================================================
  // HTTP Methods
  // =========================================================================

  private async get<T>(path: string): Promise<T> {
    return this.circuitBreaker.execute(async () => {
      const url = `${this.baseUrl}${path}`;
      
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
        },
        signal: AbortSignal.timeout(this.timeout),
      });

      if (!response.ok) {
        const error = await response.text();
        throw new Error(`Java Agent Service error: ${response.status} - ${error}`);
      }

      return response.json() as Promise<T>;
    });
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    return this.circuitBreaker.execute(async () => {
      const url = `${this.baseUrl}${path}`;
      
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(this.timeout),
      });

      if (!response.ok) {
        const error = await response.text();
        throw new Error(`Java Agent Service error: ${response.status} - ${error}`);
      }

      return response.json() as Promise<T>;
    });
  }
}

// Singleton instance
let clientInstance: JavaAgentClient | null = null;

/**
 * Get the Java Agent Client singleton instance
 */
export function getJavaAgentClient(): JavaAgentClient {
  if (!clientInstance) {
    clientInstance = new JavaAgentClient();
  }
  return clientInstance;
}

/**
 * Check if Java Agent Service is available
 */
export async function isJavaAgentServiceAvailable(): Promise<boolean> {
  try {
    const client = getJavaAgentClient();
    return await client.isHealthy();
  } catch {
    return false;
  }
}
