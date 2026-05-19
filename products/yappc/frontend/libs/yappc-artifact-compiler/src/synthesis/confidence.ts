/**
 * @fileoverview Confidence scoring for synthesized model elements.
 *
 * This module provides confidence scoring logic for semantic model elements
 * based on extraction quality, source completeness, and cross-reference validation.
 */

import type { GraphNode, ArtifactGraph } from '../graph/types';
import type { SemanticModelElement } from '../model/types';

// ============================================================================
// Confidence Factors
// ============================================================================

export interface ConfidenceFactors {
  /** Source file was fully parsed without errors */
  parseSuccess: boolean;
  /** All required fields were extracted */
  fieldCompleteness: number; // 0-1
  /** Cross-references (imports, dependencies) were resolved */
  referenceResolution: number; // 0-1
  /** Extractor confidence from metadata */
  extractorConfidence: number; // 0-1
  /** Source file follows established patterns */
  patternConformance: number; // 0-1
}

// ============================================================================
// Confidence Scorer
// ============================================================================

export class ConfidenceScorer {
  /**
   * Calculate overall confidence score from individual factors.
   * Uses weighted average with emphasis on parse success and field completeness.
   */
  calculateOverallScore(factors: ConfidenceFactors): number {
    if (!factors.parseSuccess) {
      return 0.3; // Low confidence if parsing failed
    }

    const weights = {
      fieldCompleteness: 0.4,
      referenceResolution: 0.25,
      extractorConfidence: 0.2,
      patternConformance: 0.15,
    };

    const weightedScore =
      factors.fieldCompleteness * weights.fieldCompleteness +
      factors.referenceResolution * weights.referenceResolution +
      factors.extractorConfidence * weights.extractorConfidence +
      factors.patternConformance * weights.patternConformance;

    return Math.min(1.0, Math.max(0.0, weightedScore));
  }

  /**
   * Extract confidence factors from a graph node and its context.
   */
  extractFactors(node: GraphNode, graph: ArtifactGraph): ConfidenceFactors {
    const parseSuccess = node.confidence > 0.5;
    const fieldCompleteness = this.calculateFieldCompleteness(node);
    const referenceResolution = this.calculateReferenceResolution(node, graph);
    const extractorConfidence = node.confidence;
    const patternConformance = this.calculatePatternConformance(node);

    return {
      parseSuccess,
      fieldCompleteness,
      referenceResolution,
      extractorConfidence,
      patternConformance,
    };
  }

  /**
   * Calculate field completeness based on metadata presence.
   */
  private calculateFieldCompleteness(node: GraphNode): number {
    const requiredFields = this.getRequiredFields(node.type);
    const presentFields = requiredFields.filter(field => {
      const value = node.metadata[field];
      return value !== undefined && value !== null && value !== '';
    });

    return presentFields.length / requiredFields.length;
  }

  /**
   * Calculate reference resolution based on edge completeness.
   * Checks if edges have valid target IDs (resolved references).
   */
  private calculateReferenceResolution(node: GraphNode, graph: ArtifactGraph): number {
    const outgoingEdges = graph.edges.filter(e => e.sourceId === node.id);

    if (outgoingEdges.length === 0) {
      return 1.0; // No references to resolve
    }

    // Check if target IDs are valid UUIDs (resolved) vs placeholder strings
    const resolvedEdges = outgoingEdges.filter(e => {
      // Valid UUIDs are 36 characters with dashes
      const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
      return uuidPattern.test(e.targetId);
    });

    return resolvedEdges.length / outgoingEdges.length;
  }

  /**
   * Calculate pattern conformance based on naming and structure.
   */
  private calculatePatternConformance(node: GraphNode): number {
    let score = 1.0;

    // Check naming conventions
    if (!/^[A-Z][a-zA-Z0-9]*$/.test(node.label)) {
      score -= 0.2;
    }

    // Check source location follows conventions
    const filePath = (node.sourceLocation?.filePath ?? '');
    if (filePath.includes('node_modules') || filePath.includes('.next') || filePath.includes('dist')) {
      score -= 0.5;
    }

    return Math.max(0.0, score);
  }

  /**
   * Get required fields for a given node kind.
   */
  private getRequiredFields(kind: GraphNode['type']): string[] {
    switch (kind) {
      case 'component':
        return ['props', 'name'];
      case 'page':
        return ['routePath'];
      case 'entity':
        return ['tableName', 'fields'];
      case 'state-store':
        return ['storeType'];
      case 'token':
        return ['tokenPath', 'value'];
      case 'api-endpoint':
        return ['path', 'methods'];
      case 'workflow-job':
        return ['jobs'];
      default:
        return ['name'];
    }
  }

  /**
   * Recalculate confidence for a synthesized element.
   */
  recalculateConfidence(element: SemanticModelElement, graph: ArtifactGraph): number {
    // Find the original graph node
    const node = graph.nodes.find(n => n.id === element.id);
    if (!node) {
      return element.confidence;
    }

    const factors = this.extractFactors(node, graph);
    return this.calculateOverallScore(factors);
  }

  /**
   * Determine if an element requires human review based on confidence.
   */
  requiresReview(element: SemanticModelElement, _graph: ArtifactGraph): boolean {
    const threshold = 0.7;
    return element.confidence < threshold;
  }

  /**
   * Get confidence breakdown for debugging.
   */
  getConfidenceBreakdown(node: GraphNode, graph: ArtifactGraph): ConfidenceFactors & { overall: number } {
    const factors = this.extractFactors(node, graph);
    const overall = this.calculateOverallScore(factors);

    return {
      ...factors,
      overall,
    };
  }
}

// ============================================================================
// Singleton instance
// ============================================================================

export const confidenceScorer = new ConfidenceScorer();
