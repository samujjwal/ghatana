/**
 * Auto Tagging Hook
 *
 * React hook for AI-powered automatic content classification and tagging.
 * Provides real-time tag suggestions as users type.
 *
 * @doc.type hook
 * @doc.purpose AI-powered auto-tagging
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  classifyContent,
  extractTagsFromClassification,
  type ClassificationRequest,
  type ClassificationResult,
  type TagSuggestion,
} from '../services/ai/ClassificationService';

// ============================================================================
// Types
// ============================================================================

export interface UseAutoTaggingOptions {
  contentType: ClassificationRequest['contentType'];
  existingTags?: string[];
  context?: ClassificationRequest['context'];
  debounceMs?: number;
  enabled?: boolean;
  confidenceThreshold?: number;
}

export interface UseAutoTaggingResult {
  content: string;
  setContent: (content: string) => void;
  classification: ClassificationResult | null;
  suggestedTags: TagSuggestion[];
  isLoading: boolean;
  error: Error | null;
  acceptTag: (tag: string) => void;
  rejectTag: (tag: string) => void;
  acceptAllTags: () => void;
  clearContent: () => void;
  refresh: () => void;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useAutoTagging({
  contentType,
  existingTags = [],
  context,
  debounceMs = 500,
  enabled = true,
  confidenceThreshold = 0.5,
}: UseAutoTaggingOptions): UseAutoTaggingResult {
  const queryClient = useQueryClient();
  const [content, setContent] = useState('');
  const [debouncedContent, setDebouncedContent] = useState('');
  const [acceptedTags, setAcceptedTags] = useState<Set<string>>(new Set());
  const [rejectedTags, setRejectedTags] = useState<Set<string>>(new Set());

  // Debounce content changes
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedContent(content);
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [content, debounceMs]);

  // Query for classification
  const {
    data: classificationResponse,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['classification', debouncedContent, contentType, context],
    queryFn: () =>
      classifyContent({
        content: debouncedContent,
        contentType,
        existingTags: [...existingTags, ...Array.from(acceptedTags)],
        context,
      }),
    enabled: enabled && debouncedContent.length > 10,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

  const classification = classificationResponse?.result || null;
  const suggestedTags = classification?.suggestedTags
    .filter(s => !rejectedTags.has(s.tag) && !acceptedTags.has(s.tag) && s.confidence >= confidenceThreshold)
    || [];

  // Accept a tag
  const acceptTag = useCallback((tag: string) => {
    setAcceptedTags(prev => new Set(prev).add(tag));
    setRejectedTags(prev => {
      const next = new Set(prev);
      next.delete(tag);
      return next;
    });
  }, []);

  // Reject a tag
  const rejectTag = useCallback((tag: string) => {
    setRejectedTags(prev => new Set(prev).add(tag));
    setAcceptedTags(prev => {
      const next = new Set(prev);
      next.delete(tag);
      return next;
    });
  }, []);

  // Accept all suggested tags
  const acceptAllTags = useCallback(() => {
    const newAccepted = new Set(acceptedTags);
    suggestedTags.forEach(s => newAccepted.add(s.tag));
    setAcceptedTags(newAccepted);
    setRejectedTags(new Set());
  }, [acceptedTags, suggestedTags]);

  // Clear content
  const clearContent = useCallback(() => {
    setContent('');
    setAcceptedTags(new Set());
    setRejectedTags(new Set());
  }, []);

  return {
    content,
    setContent,
    classification,
    suggestedTags,
    isLoading,
    error,
    acceptTag,
    rejectTag,
    acceptAllTags,
    clearContent,
    refresh: refetch,
  };
}
