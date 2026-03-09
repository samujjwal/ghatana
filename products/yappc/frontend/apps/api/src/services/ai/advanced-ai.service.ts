/**
 * Advanced AI Features - Multi-Model Routing and Context-Aware Suggestions
 * 
 * Implements intelligent AI provider selection, context-aware suggestion generation,
 * and advanced AI capabilities for the YAPPC platform.
 * 
 * @doc.type service
 * @doc.purpose Advanced AI features with multi-model routing
 * @doc.layer product
 * @doc.pattern AI Service
 */

import { z } from 'zod';

// ============================================================================
// Types and Interfaces
// ============================================================================

export interface AIProvider {
  id: string;
  name: string;
  capabilities: AIProviderCapabilities;
  costPerToken: number;
  maxTokens: number;
  latency: number;
  reliability: number;
  specialties: string[];
}

export interface AIProviderCapabilities {
  textGeneration: boolean;
  codeGeneration: boolean;
  imageGeneration: boolean;
  streaming: boolean;
  functionCalling: boolean;
  contextWindow: number;
}

export interface AIRequest {
  id: string;
  type: 'text' | 'code' | 'image' | 'analysis';
  prompt: string;
  context: AIContext;
  priority: 'low' | 'medium' | 'high' | 'critical';
  maxTokens?: number;
  temperature?: number;
  streaming?: boolean;
}

export interface AIContext {
  projectId?: string;
  workspaceId?: string;
  currentPhase?: string;
  currentMode?: string;
  userRole?: string;
  recentInteractions: AIInteraction[];
  projectType?: string;
  technologies: string[];
  domain: string;
  userPreferences: UserPreferences;
}

export interface AIInteraction {
  timestamp: string;
  type: string;
  provider: string;
  success: boolean;
  latency: number;
  userFeedback?: 'positive' | 'negative' | 'neutral';
}

export interface UserPreferences {
  preferredProviders: string[];
  avoidProviders: string[];
  costSensitivity: 'low' | 'medium' | 'high';
  speedPreference: 'fast' | 'balanced' | 'quality';
  specialtyPreference: boolean;
}

export interface AISuggestion {
  id: string;
  type: 'component' | 'code' | 'architecture' | 'workflow' | 'insight';
  title: string;
  description: string;
  content: unknown;
  confidence: number;
  provider: string;
  reasoning: string;
  context: string[];
  estimatedCost: number;
  estimatedTime: number;
}

export interface MultiModelRouter {
  selectProvider(request: AIRequest): AIProvider;
  routeRequest(request: AIRequest): Promise<AISuggestion[]>;
  getProviderMetrics(): ProviderMetrics;
  updateProviderPerformance(providerId: string, metrics: PerformanceMetrics): void;
}

export interface ProviderMetrics {
  [providerId: string]: {
    successRate: number;
    averageLatency: number;
    averageCost: number;
    userSatisfaction: number;
    requestCount: number;
    errorCount: number;
  };
}

export interface PerformanceMetrics {
  success: boolean;
  latency: number;
  cost: number;
  userFeedback?: 'positive' | 'negative' | 'neutral';
  error?: string;
}

// ============================================================================
// AI Provider Registry
// ============================================================================

class AIProviderRegistry {
  private providers: Map<string, AIProvider> = new Map();
  private metrics: ProviderMetrics = {};

  constructor() {
    this.initializeProviders();
  }

  private initializeProviders(): void {
    // OpenAI GPT-4
    this.providers.set('openai-gpt4', {
      id: 'openai-gpt4',
      name: 'OpenAI GPT-4',
      capabilities: {
        textGeneration: true,
        codeGeneration: true,
        imageGeneration: false,
        streaming: true,
        functionCalling: true,
        contextWindow: 8192,
      },
      costPerToken: 0.00003,
      maxTokens: 4096,
      latency: 2000,
      reliability: 0.95,
      specialties: ['code', 'analysis', 'reasoning'],
    });

    // OpenAI GPT-3.5 Turbo
    this.providers.set('openai-gpt35-turbo', {
      id: 'openai-gpt35-turbo',
      name: 'OpenAI GPT-3.5 Turbo',
      capabilities: {
        textGeneration: true,
        codeGeneration: true,
        imageGeneration: false,
        streaming: true,
        functionCalling: true,
        contextWindow: 4096,
      },
      costPerToken: 0.000001,
      maxTokens: 4096,
      latency: 800,
      reliability: 0.90,
      specialties: ['text', 'code', 'speed'],
    });

    // Anthropic Claude
    this.providers.set('anthropic-claude', {
      id: 'anthropic-claude',
      name: 'Anthropic Claude',
      capabilities: {
        textGeneration: true,
        codeGeneration: true,
        imageGeneration: false,
        streaming: true,
        functionCalling: false,
        contextWindow: 100000,
      },
      costPerToken: 0.000008,
      maxTokens: 4096,
      latency: 1500,
      reliability: 0.92,
      specialties: ['analysis', 'writing', 'reasoning'],
    });

    // Google Gemini
    this.providers.set('google-gemini', {
      id: 'google-gemini',
      name: 'Google Gemini',
      capabilities: {
        textGeneration: true,
        codeGeneration: true,
        imageGeneration: true,
        streaming: true,
        functionCalling: true,
        contextWindow: 32768,
      },
      costPerToken: 0.000001,
      maxTokens: 8192,
      latency: 1200,
      reliability: 0.88,
      specialties: ['multimodal', 'code', 'analysis'],
    });

    // Initialize metrics
    this.providers.forEach((provider, id) => {
      this.metrics[id] = {
        successRate: 0.9,
        averageLatency: provider.latency,
        averageCost: provider.costPerToken,
        userSatisfaction: 0.8,
        requestCount: 0,
        errorCount: 0,
      };
    });
  }

  getProvider(id: string): AIProvider | undefined {
    return this.providers.get(id);
  }

  getAllProviders(): AIProvider[] {
    return Array.from(this.providers.values());
  }

  getProvidersByCapability(capability: keyof AIProviderCapabilities): AIProvider[] {
    return this.getAllProviders().filter(provider => provider.capabilities[capability]);
  }

  getProvidersBySpecialty(specialty: string): AIProvider[] {
    return this.getAllProviders().filter(provider => 
      provider.specialties.includes(specialty)
    );
  }

  updateMetrics(providerId: string, metrics: PerformanceMetrics): void {
    const current = this.metrics[providerId];
    if (current) {
      current.requestCount++;
      if (!metrics.success) {
        current.errorCount++;
      }
      
      // Update rolling averages
      const alpha = 0.1; // Learning rate
      current.successRate = current.successRate * (1 - alpha) + (metrics.success ? 1 : 0) * alpha;
      current.averageLatency = current.averageLatency * (1 - alpha) + metrics.latency * alpha;
      current.averageCost = current.averageCost * (1 - alpha) + metrics.cost * alpha;
      
      if (metrics.userFeedback) {
        const feedbackScore = metrics.userFeedback === 'positive' ? 1 : 
                           metrics.userFeedback === 'negative' ? 0 : 0.5;
        current.userSatisfaction = current.userSatisfaction * (1 - alpha) + feedbackScore * alpha;
      }
    }
  }

  getMetrics(): ProviderMetrics {
    return { ...this.metrics };
  }
}

// ============================================================================
// Context-Aware Suggestion Engine
// ============================================================================

class ContextAwareSuggestionEngine {
  private providerRegistry: AIProviderRegistry;
  private suggestionCache: Map<string, AISuggestion[]> = new Map();
  private contextAnalyzer: ContextAnalyzer;

  constructor(providerRegistry: AIProviderRegistry) {
    this.providerRegistry = providerRegistry;
    this.contextAnalyzer = new ContextAnalyzer();
  }

  async generateSuggestions(request: AIRequest): Promise<AISuggestion[]> {
    // Check cache first
    const cacheKey = this.generateCacheKey(request);
    const cached = this.suggestionCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    // Analyze context
    const contextAnalysis = this.contextAnalyzer.analyze(request.context);
    
    // Select appropriate providers
    const providers = this.selectProvidersForContext(request, contextAnalysis);
    
    // Generate suggestions from multiple providers
    const suggestions: AISuggestion[] = [];
    
    for (const provider of providers) {
      try {
        const providerSuggestions = await this.generateProviderSuggestions(provider, request, contextAnalysis);
        suggestions.push(...providerSuggestions);
      } catch (error) {
        console.error(`Error generating suggestions from ${provider.name}:`, error);
      }
    }

    // Rank and filter suggestions
    const rankedSuggestions = this.rankSuggestions(suggestions, contextAnalysis);
    
    // Cache results
    this.suggestionCache.set(cacheKey, rankedSuggestions);
    
    return rankedSuggestions;
  }

  private generateCacheKey(request: AIRequest): string {
    return `${request.type}-${request.prompt.substring(0, 100)}-${JSON.stringify(request.context)}`;
  }

  private selectProvidersForContext(request: AIRequest, analysis: ContextAnalysis): AIProvider[] {
    const providers = this.providerRegistry.getAllProviders();
    
    // Filter by capabilities
    const capableProviders = providers.filter(provider => {
      switch (request.type) {
        case 'code':
          return provider.capabilities.codeGeneration;
        case 'image':
          return provider.capabilities.imageGeneration;
        default:
          return provider.capabilities.textGeneration;
      }
    });

    // Sort by context relevance
    return capableProviders.sort((a, b) => {
      const aScore = this.calculateProviderScore(a, request, analysis);
      const bScore = this.calculateProviderScore(b, request, analysis);
      return bScore - aScore;
    }).slice(0, 3); // Top 3 providers
  }

  private calculateProviderScore(provider: AIProvider, request: AIRequest, analysis: ContextAnalysis): number {
    let score = 0;

    // Base reliability score
    score += provider.reliability * 0.3;

    // Specialty matching
    const matchingSpecialties = provider.specialties.filter(specialty => 
      analysis.relevantSpecialties.includes(specialty)
    );
    score += (matchingSpecialties.length / provider.specialties.length) * 0.2;

    // Cost consideration
    const metrics = this.providerRegistry.getMetrics()[provider.id];
    if (request.context.userPreferences.costSensitivity === 'high') {
      score += (1 / provider.costPerToken) * 0.1;
    }

    // Speed consideration
    if (request.context.userPreferences.speedPreference === 'fast') {
      score += (1 / provider.latency) * 0.1;
    }

    // Context window consideration
    if (analysis.estimatedTokens > provider.capabilities.contextWindow * 0.8) {
      score -= 0.2; // Penalty for insufficient context
    }

    // User preference
    if (request.context.userPreferences.preferredProviders.includes(provider.id)) {
      score += 0.1;
    }
    if (request.context.userPreferences.avoidProviders.includes(provider.id)) {
      score -= 0.5;
    }

    return score;
  }

  private async generateProviderSuggestions(provider: AIProvider, request: AIRequest, analysis: ContextAnalysis): Promise<AISuggestion[]> {
    // This would integrate with the actual AI provider APIs
    // For now, we'll simulate the response
    
    const suggestions: AISuggestion[] = [];
    
    // Generate different types of suggestions based on context
    if (request.type === 'code') {
      suggestions.push(...this.generateCodeSuggestions(provider, request, analysis));
    } else if (request.type === 'analysis') {
      suggestions.push(...this.generateAnalysisSuggestions(provider, request, analysis));
    } else {
      suggestions.push(...this.generateTextSuggestions(provider, request, analysis));
    }

    return suggestions.map(suggestion => ({
      ...suggestion,
      provider: provider.id,
      estimatedCost: this.estimateCost(suggestion, provider),
      estimatedTime: this.estimateTime(suggestion, provider),
    }));
  }

  private generateCodeSuggestions(provider: AIProvider, request: AIRequest, analysis: ContextAnalysis): AISuggestion[] {
    return [
      {
        id: `${provider.id}-code-1`,
        type: 'code',
        title: 'React Component',
        description: 'Generate a React component based on the current context',
        content: {
          framework: 'react',
          language: 'typescript',
          code: '// Generated code would go here',
        },
        confidence: 0.85,
        provider: provider.id,
        reasoning: 'Based on the React/TypeScript stack detected in the project',
        context: ['project-type', 'technologies'],
        estimatedCost: 0.01,
        estimatedTime: 2000,
      },
      {
        id: `${provider.id}-code-2`,
        type: 'code',
        title: 'API Endpoint',
        description: 'Create an API endpoint for the current feature',
        content: {
          framework: 'express',
          language: 'typescript',
          code: '// Generated API code would go here',
        },
        confidence: 0.78,
        provider: provider.id,
        reasoning: 'API endpoint needed for the current workspace functionality',
        context: ['project-type', 'current-phase'],
        estimatedCost: 0.008,
        estimatedTime: 1500,
      },
    ];
  }

  private generateAnalysisSuggestions(provider: AIProvider, request: AIRequest, analysis: ContextAnalysis): AISuggestion[] {
    return [
      {
        id: `${provider.id}-analysis-1`,
        type: 'insight',
        title: 'Architecture Analysis',
        description: 'Analyze the current architecture and suggest improvements',
        content: {
          findings: [
            'Consider microservices for better scalability',
            'Add caching layer for performance',
            'Implement proper error handling',
          ],
          recommendations: [
            'Use Redis for session management',
            'Add API rate limiting',
            'Implement database connection pooling',
          ],
        },
        confidence: 0.82,
        provider: provider.id,
        reasoning: 'Architecture patterns identified from project structure',
        context: ['project-type', 'technologies'],
        estimatedCost: 0.015,
        estimatedTime: 3000,
      },
    ];
  }

  private generateTextSuggestions(provider: AIProvider, request: AIRequest, analysis: ContextAnalysis): AISuggestion[] {
    return [
      {
        id: `${provider.id}-text-1`,
        type: 'workflow',
        title: 'Project Documentation',
        description: 'Generate comprehensive documentation for the current phase',
        content: {
          sections: [
            'Project Overview',
            'Architecture Decisions',
            'Development Guidelines',
            'Deployment Instructions',
          ],
        },
        confidence: 0.90,
        provider: provider.id,
        reasoning: 'Documentation is essential for the current development phase',
        context: ['current-phase', 'project-type'],
        estimatedCost: 0.012,
        estimatedTime: 2500,
      },
    ];
  }

  private rankSuggestions(suggestions: AISuggestion[], analysis: ContextAnalysis): AISuggestion[] {
    return suggestions.sort((a, b) => {
      // Primary sort by confidence
      const confidenceDiff = b.confidence - a.confidence;
      if (Math.abs(confidenceDiff) > 0.1) {
        return confidenceDiff;
      }

      // Secondary sort by context relevance
      const aRelevance = this.calculateContextRelevance(a, analysis);
      const bRelevance = this.calculateContextRelevance(b, analysis);
      return bRelevance - aRelevance;
    }).slice(0, 10); // Top 10 suggestions
  }

  private calculateContextRelevance(suggestion: AISuggestion, analysis: ContextAnalysis): number {
    let relevance = 0;

    // Check if suggestion type matches context needs
    if (analysis.needs.includes(suggestion.type)) {
      relevance += 0.5;
    }

    // Check if suggestion context matches current context
    const matchingContext = suggestion.context.filter(ctx => 
      analysis.currentContext.includes(ctx)
    );
    relevance += (matchingContext.length / suggestion.context.length) * 0.3;

    // Check provider reliability
    const provider = this.providerRegistry.getProvider(suggestion.provider);
    if (provider) {
      relevance += provider.reliability * 0.2;
    }

    return relevance;
  }

  private estimateCost(suggestion: AISuggestion, provider: AIProvider): number {
    // Rough estimation based on content length and provider cost
    const contentLength = JSON.stringify(suggestion.content).length;
    const tokens = Math.ceil(contentLength / 4); // Rough token estimation
    return tokens * provider.costPerToken;
  }

  private estimateTime(suggestion: AISuggestion, provider: AIProvider): number {
    // Base time plus provider latency
    const baseTime = 1000; // 1 second base processing
    return baseTime + provider.latency;
  }
}

// ============================================================================
// Context Analyzer
// ============================================================================

interface ContextAnalysis {
  relevantSpecialties: string[];
  estimatedTokens: number;
  needs: string[];
  currentContext: string[];
  complexity: 'low' | 'medium' | 'high';
  domain: string;
}

class ContextAnalyzer {
  analyze(context: AIContext): ContextAnalysis {
    const specialties = this.extractSpecialties(context);
    const tokens = this.estimateTokens(context);
    const needs = this.identifyNeeds(context);
    const currentContext = this.extractCurrentContext(context);
    const complexity = this.assessComplexity(context);
    const domain = this.identifyDomain(context);

    return {
      relevantSpecialties: specialties,
      estimatedTokens: tokens,
      needs,
      currentContext,
      complexity,
      domain,
    };
  }

  private extractSpecialties(context: AIContext): string[] {
    const specialties: string[] = [];
    
    // From technologies
    if (context.technologies.includes('react') || context.technologies.includes('typescript')) {
      specialties.push('code', 'frontend');
    }
    if (context.technologies.includes('node') || context.technologies.includes('express')) {
      specialties.push('code', 'backend');
    }
    if (context.technologies.includes('python') || context.technologies.includes('tensorflow')) {
      specialties.push('ai', 'ml');
    }

    // From domain
    if (context.domain === 'e-commerce') {
      specialties.push('business', 'analysis');
    }
    if (context.domain === 'healthcare') {
      specialties.push('analysis', 'compliance');
    }

    return specialties;
  }

  private estimateTokens(context: AIContext): number {
    let tokens = 100; // Base tokens
    
    tokens += context.technologies.length * 10;
    tokens += context.recentInteractions.length * 20;
    tokens += JSON.stringify(context.userPreferences).length / 4;
    
    return tokens;
  }

  private identifyNeeds(context: AIContext): string[] {
    const needs: string[] = [];
    
    // Based on current phase
    if (context.currentPhase === 'INTENT') {
      needs.push('analysis', 'planning');
    } else if (context.currentPhase === 'GENERATE') {
      needs.push('code', 'architecture');
    } else if (context.currentPhase === 'OBSERVE') {
      needs.push('analysis', 'monitoring');
    }

    // Based on project type
    if (context.projectType === 'web-app') {
      needs.push('frontend', 'backend');
    } else if (context.projectType === 'mobile-app') {
      needs.push('mobile', 'frontend');
    }

    return needs;
  }

  private extractCurrentContext(context: AIContext): string[] {
    const currentContext: string[] = [];
    
    if (context.projectType) currentContext.push('project-type');
    if (context.currentPhase) currentContext.push('current-phase');
    if (context.currentMode) currentContext.push('current-mode');
    if (context.userRole) currentContext.push('user-role');
    if (context.domain) currentContext.push('domain');
    
    return currentContext;
  }

  private assessComplexity(context: AIContext): 'low' | 'medium' | 'high' {
    let complexityScore = 0;
    
    // Technology stack complexity
    complexityScore += context.technologies.length * 0.1;
    
    // Recent interactions complexity
    complexityScore += context.recentInteractions.length * 0.05;
    
    // Domain complexity
    if (context.domain === 'healthcare' || context.domain === 'finance') {
      complexityScore += 0.3;
    }
    
    if (complexityScore < 0.3) return 'low';
    if (complexityScore < 0.7) return 'medium';
    return 'high';
  }

  private identifyDomain(context: AIContext): string {
    // Try to identify domain from technologies and context
    if (context.technologies.includes('tensorflow') || context.technologies.includes('pytorch')) {
      return 'ai-ml';
    }
    if (context.technologies.includes('react') || context.technologies.includes('vue')) {
      return 'web-development';
    }
    if (context.technologies.includes('react-native') || context.technologies.includes('flutter')) {
      return 'mobile-development';
    }
    
    return context.domain || 'general';
  }
}

// ============================================================================
// Multi-Model Router Implementation
// ============================================================================

export class AdvancedMultiModelRouter implements MultiModelRouter {
  private providerRegistry: AIProviderRegistry;
  private suggestionEngine: ContextAwareSuggestionEngine;

  constructor() {
    this.providerRegistry = new AIProviderRegistry();
    this.suggestionEngine = new ContextAwareSuggestionEngine(this.providerRegistry);
  }

  selectProvider(request: AIRequest): AIProvider {
    const providers = this.providerRegistry.getAllProviders();
    
    // Filter by capabilities
    const capableProviders = providers.filter(provider => {
      switch (request.type) {
        case 'code':
          return provider.capabilities.codeGeneration;
        case 'image':
          return provider.capabilities.imageGeneration;
        default:
          return provider.capabilities.textGeneration;
      }
    });

    // Sort by performance metrics
    const metrics = this.providerRegistry.getMetrics();
    
    return capableProviders.sort((a, b) => {
      const aMetrics = metrics[a.id];
      const bMetrics = metrics[b.id];
      
      // Consider success rate, latency, and cost
      const aScore = (aMetrics.successRate * 0.4) + 
                    (1 / aMetrics.averageLatency * 0.3) + 
                    (1 / aMetrics.averageCost * 0.3);
      const bScore = (bMetrics.successRate * 0.4) + 
                    (1 / bMetrics.averageLatency * 0.3) + 
                    (1 / bMetrics.averageCost * 0.3);
      
      return bScore - aScore;
    })[0];
  }

  async routeRequest(request: AIRequest): Promise<AISuggestion[]> {
    try {
      const suggestions = await this.suggestionEngine.generateSuggestions(request);
      
      // Update provider metrics based on successful request
      const provider = this.selectProvider(request);
      this.providerRegistry.updateMetrics(provider.id, {
        success: true,
        latency: 1000, // Would be actual latency
        cost: suggestions.reduce((total, s) => total + s.estimatedCost, 0),
      });
      
      return suggestions;
    } catch (error) {
      // Update provider metrics based on failed request
      const provider = this.selectProvider(request);
      this.providerRegistry.updateMetrics(provider.id, {
        success: false,
        latency: 5000,
        cost: 0,
        error: error.message,
      });
      
      throw error;
    }
  }

  getProviderMetrics(): ProviderMetrics {
    return this.providerRegistry.getMetrics();
  }

  updateProviderPerformance(providerId: string, metrics: PerformanceMetrics): void {
    this.providerRegistry.updateMetrics(providerId, metrics);
  }
}

// ============================================================================
// Advanced AI Service
// ============================================================================

export class AdvancedAIService {
  private router: AdvancedMultiModelRouter;
  private requestHistory: Map<string, AIRequest> = new Map();
  private userFeedback: Map<string, 'positive' | 'negative' | 'neutral'> = new Map();

  constructor() {
    this.router = new AdvancedMultiModelRouter();
  }

  async generateContextAwareSuggestions(context: AIContext, prompt: string, type: AIRequest['type'] = 'text'): Promise<AISuggestion[]> {
    const request: AIRequest = {
      id: this.generateRequestId(),
      type,
      prompt,
      context,
      priority: 'medium',
      streaming: false,
    };

    this.requestHistory.set(request.id, request);
    
    try {
      const suggestions = await this.router.routeRequest(request);
      return suggestions;
    } catch (error) {
      console.error('Error generating suggestions:', error);
      throw error;
    }
  }

  async generateMultiProviderSuggestions(context: AIContext, prompt: string): Promise<AISuggestion[]> {
    // Generate suggestions from multiple providers and combine them
    const providers = this.router.getProviderMetrics();
    
    const allSuggestions: AISuggestion[] = [];
    
    for (const providerId of Object.keys(providers)) {
      try {
        const request: AIRequest = {
          id: this.generateRequestId(),
          type: 'text',
          prompt,
          context: {
            ...context,
            userPreferences: {
              ...context.userPreferences,
              preferredProviders: [providerId],
            },
          },
          priority: 'medium',
        };

        const suggestions = await this.router.routeRequest(request);
        allSuggestions.push(...suggestions);
      } catch (error) {
        console.error(`Error with provider ${providerId}:`, error);
      }
    }

    // Rank and return top suggestions
    return allSuggestions
      .sort((a, b) => b.confidence - a.confidence)
      .slice(0, 20);
  }

  async generateStreamingSuggestions(context: AIContext, prompt: string, onChunk: (chunk: string) => void): Promise<void> {
    const request: AIRequest = {
      id: this.generateRequestId(),
      type: 'text',
      prompt,
      context,
      priority: 'high',
      streaming: true,
    };

    // This would implement actual streaming with the selected provider
    // For now, we'll simulate it
    const provider = this.router.selectProvider(request);
    
    if (!provider.capabilities.streaming) {
      throw new Error(`Provider ${provider.name} does not support streaming`);
    }

    // Simulate streaming
    const chunks = prompt.split(' ');
    for (const chunk of chunks) {
      onChunk(chunk + ' ');
      await new Promise(resolve => setTimeout(resolve, 100));
    }
  }

  submitFeedback(suggestionId: string, feedback: 'positive' | 'negative' | 'neutral'): void {
    this.userFeedback.set(suggestionId, feedback);
    
    // Update provider metrics based on feedback
    // This would typically involve finding the provider for the suggestion
    // and updating their user satisfaction score
  }

  getProviderMetrics(): ProviderMetrics {
    return this.router.getProviderMetrics();
  }

  getAvailableProviders(): AIProvider[] {
    return this.router.getProviderMetrics ? 
      Object.keys(this.router.getProviderMetrics()).map(id => 
        this.router.getProvider ? this.router.getProvider(id) : null
      ).filter(Boolean) as AIProvider[] :
      [];
  }

  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}

// ============================================================================
// Exports
// ============================================================================

export const advancedAIService = new AdvancedAIService();
export { AIProviderRegistry, ContextAwareSuggestionEngine };
