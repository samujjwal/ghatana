/**
 * React Query Hooks for Flashit API
 * Manages server state with caching, refetching, and optimistic updates
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/api-client';
import { useAtom, useSetAtom } from 'jotai';
import { authTokenAtom, currentUserAtom, spheresAtom } from '../store/atoms';

// Auth hooks
export const useRegister = () => {
  const setToken = useSetAtom(authTokenAtom);
  const setCurrentUser = useSetAtom(currentUserAtom);

  return useMutation({
    mutationFn: (data: { email: string; password: string; displayName?: string }) =>
      apiClient.register(data),
    onSuccess: (data) => {
      setToken(data.accessToken);
      apiClient.setToken(data.accessToken);
      setCurrentUser(data.user);
    },
  });
};

export const useLogin = () => {
  const setToken = useSetAtom(authTokenAtom);
  const setCurrentUser = useSetAtom(currentUserAtom);

  return useMutation({
    mutationFn: (data: { email: string; password: string }) =>
      apiClient.login(data),
    onSuccess: (data) => {
      setToken(data.accessToken);
      apiClient.setToken(data.accessToken);
      setCurrentUser(data.user);
    },
  });
};

export const useLogout = () => {
  const setToken = useSetAtom(authTokenAtom);
  const setCurrentUser = useSetAtom(currentUserAtom);
  const queryClient = useQueryClient();

  return () => {
    setToken(null);
    setCurrentUser(null);
    apiClient.clearToken();
    queryClient.clear();
    window.location.href = '/login';
  };
};

export const useCurrentUser = (options?: { enabled?: boolean }) => {
  const [token] = useAtom(authTokenAtom);
  const setCurrentUser = useSetAtom(currentUserAtom);

  // Check if we have a token in either the atom or localStorage
  const storedToken = localStorage.getItem('flashit_token') || token;
  const hasToken = !!storedToken;
  const isEnabled = options?.enabled !== false && hasToken;

  return useQuery({
    queryKey: ['currentUser', storedToken],
    queryFn: async () => {
      if (!storedToken) {
        throw new Error('No token available');
      }
      const user = await apiClient.getCurrentUser();
      setCurrentUser(user);
      return user;
    },
    enabled: isEnabled, // Only run the query if enabled and we have a token
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: (failureCount, error) => {
      // Only retry if it's not a 401/403 error (auth errors)
      if (error?.message?.includes('401') || error?.message?.includes('403') || error?.message?.includes('Unauthorized')) {
        return false;
      }
      return failureCount < 2;
    },
    retryDelay: 1000,
  });
};

// Spheres hooks
export const useSpheres = (ownedOnly = false) => {
  const setSpheres = useSetAtom(spheresAtom);

  return useQuery({
    queryKey: ['spheres', ownedOnly],
    queryFn: async () => {
      const data = await apiClient.getSpheres(ownedOnly);
      setSpheres(data.spheres);
      return data.spheres;
    },
    staleTime: 2 * 60 * 1000, // 2 minutes
  });
};

export const useSphere = (id: string) => {
  return useQuery({
    queryKey: ['sphere', id],
    queryFn: async () => {
      const data = await apiClient.getSphere(id);
      return data.sphere;
    },
    enabled: !!id,
  });
};

export const useCreateSphere = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      name: string;
      description?: string;
      type: string;
      visibility: string;
    }) => apiClient.createSphere(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
    },
  });
};

export const useUpdateSphere = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: {
      id: string;
      data: Partial<{ name: string; description: string; visibility: string }>;
    }) => apiClient.updateSphere(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['sphere', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
    },
  });
};

export const useDeleteSphere = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.deleteSphere(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
    },
  });
};

// Moments hooks
export const useCreateMoment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      sphereId?: string;
      content: {
        text: string;
        transcript?: string;
        type: 'TEXT' | 'VOICE' | 'VIDEO' | 'IMAGE' | 'MIXED';
      };
      signals?: {
        emotions?: string[];
        tags?: string[];
        intent?: string;
        sentimentScore?: number;
        importance?: number;
        entities?: string[];
      };
      metadata?: Record<string, unknown>;
      capturedAt?: string;
    }) => apiClient.createMoment(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['moments'] });
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
    },
  });
};

export const useClassifySphereAI = () => {
  return useMutation({
    mutationFn: (data: {
      content: {
        text: string;
        transcript?: string;
        type: 'TEXT' | 'VOICE' | 'VIDEO' | 'IMAGE' | 'MIXED';
      };
      signals?: {
        emotions?: string[];
        tags?: string[];
        intent?: string;
        sentimentScore?: number;
        importance?: number;
        entities?: string[];
      };
    }) => apiClient.classifySphereAI(data),
  });
};

export const useMoment = (id: string) => {
  return useQuery({
    queryKey: ['moment', id],
    queryFn: async () => {
      const data = await apiClient.getMoment(id);
      return data.moment;
    },
    enabled: !!id,
  });
};

export const useSearchMoments = (params?: {
  sphereIds?: string[];
  query?: string;
  tags?: string[];
  emotions?: string[];
  startDate?: string;
  endDate?: string;
  limit?: number;
  cursor?: string;
}) => {
  return useQuery({
    queryKey: ['moments', params],
    queryFn: async () => {
      // Use standard getMoments which supports filtering and simple search
      // TODO: Future: Integrate AI search here if query is present, with response mapping
      return apiClient.getMoments(params);
    },
    staleTime: 30 * 1000, // 30 seconds
  });
};

export const useDeleteMoment = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => apiClient.deleteMoment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['moments'] });
      queryClient.invalidateQueries({ queryKey: ['spheres'] });
    },
  });
};

// Moment Links (Temporal Arcs) hooks
export const useMomentLinks = (momentId: string, params?: {
  direction?: 'outgoing' | 'incoming' | 'both';
  linkType?: string;
  limit?: number;
}) => {
  return useQuery({
    queryKey: ['moment-links', momentId, params],
    queryFn: () => apiClient.getMomentLinks(momentId, params),
    enabled: !!momentId,
    staleTime: 30 * 1000,
  });
};

export const useCreateMomentLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ momentId, data }: {
      momentId: string;
      data: { targetMomentId: string; linkType: string; metadata?: Record<string, unknown> };
    }) => apiClient.createMomentLink(momentId, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['moment-links', variables.momentId] });
      queryClient.invalidateQueries({ queryKey: ['moment-links', variables.data.targetMomentId] });
      queryClient.invalidateQueries({ queryKey: ['link-graph'] });
    },
  });
};

export const useDeleteMomentLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ momentId, linkId }: { momentId: string; linkId: string }) =>
      apiClient.deleteMomentLink(momentId, linkId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['moment-links'] });
      queryClient.invalidateQueries({ queryKey: ['link-graph'] });
    },
  });
};

export const useLinkGraph = (sphereId: string, params?: {
  depth?: number;
  linkTypes?: string;
  limit?: number;
}) => {
  return useQuery({
    queryKey: ['link-graph', sphereId, params],
    queryFn: () => apiClient.getLinkGraph(sphereId, params),
    enabled: !!sphereId,
    staleTime: 60 * 1000,
  });
};

export const useLinkSuggestions = (momentId: string, params?: {
  limit?: number;
  threshold?: number;
}) => {
  return useQuery({
    queryKey: ['link-suggestions', momentId, params],
    queryFn: () => apiClient.getLinkSuggestions(momentId, params),
    enabled: !!momentId,
    staleTime: 60 * 1000,
  });
};

export const useLinkStats = (sphereId: string) => {
  return useQuery({
    queryKey: ['link-stats', sphereId],
    queryFn: () => apiClient.getMomentLinkStats(sphereId),
    enabled: !!sphereId,
    staleTime: 60 * 1000,
  });
};

export const useLinkTimeline = (sphereId: string, params?: {
  startDate?: string;
  endDate?: string;
  linkTypes?: string[];
  groupBy?: 'day' | 'week' | 'month';
}) => {
  return useQuery({
    queryKey: ['link-timeline', sphereId, params],
    queryFn: () => apiClient.getMomentLinkTimeline(sphereId, params),
    enabled: !!sphereId,
    staleTime: 60 * 1000,
  });
};

// Meaning Metrics hooks
export const useMeaningMetrics = (params?: {
  sphereId?: string;
  startDate?: string;
  endDate?: string;
}) => {
  return useQuery({
    queryKey: ['meaning-metrics', params],
    queryFn: () => apiClient.getMeaningMetrics(params),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useCompareMeaningMetrics = (params: {
  period1Start: string;
  period1End: string;
  period2Start: string;
  period2End: string;
  sphereId?: string;
}) => {
  return useQuery({
    queryKey: ['meaning-metrics-compare', params],
    queryFn: () => apiClient.compareMeaningMetrics(params),
    staleTime: 5 * 60 * 1000,
  });
};

export const useRecordMeaningEvent = () => {
  return useMutation({
    mutationFn: (data: {
      eventType: string;
      momentId?: string;
      sphereId?: string;
      metadata?: Record<string, unknown>;
    }) => apiClient.recordMeaningEvent(data),
  });
};

// Language Evolution hooks
export const useLanguageEvolution = (params?: {
  periodDays?: number;
  sphereId?: string;
}) => {
  return useQuery({
    queryKey: ['language-evolution', params],
    queryFn: () => apiClient.getLanguageEvolution(params),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

export const useReturnToMeaningRate = (params?: {
  periodDays?: number;
  sphereId?: string;
}) => {
  return useQuery({
    queryKey: ['return-to-meaning-rate', params],
    queryFn: () => apiClient.getReturnToMeaningRate(params),
    staleTime: 5 * 60 * 1000,
  });
};

export const useCrossTimeReferencing = (params?: {
  periodDays?: number;
  sphereId?: string;
}) => {
  return useQuery({
    queryKey: ['cross-time-referencing', params],
    queryFn: () => apiClient.getCrossTimeReferencing(params),
    staleTime: 5 * 60 * 1000,
  });
};

// Memory Expansion hooks
export const useRequestMemoryExpansion = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      sphereId?: string;
      momentIds?: string[];
      expansionType: 'summarize' | 'extract_themes' | 'identify_patterns' | 'find_connections';
      timeRange?: {
        startDate: string;
        endDate: string;
      };
      priority?: 'high' | 'normal' | 'low';
    }) => apiClient.requestMemoryExpansion(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-expansions'] });
    },
  });
};

export const useExpansionResult = (jobId: string, enabled: boolean = true) => {
  return useQuery({
    queryKey: ['expansion-result', jobId],
    queryFn: () => apiClient.getExpansionResult(jobId),
    enabled: enabled && !!jobId,
    refetchInterval: (data) => {
      // Poll every 2 seconds if pending or processing
      if (data?.status === 'pending' || data?.status === 'processing') {
        return 2000;
      }
      return false;
    },
    staleTime: 0,
  });
};

export const useUserExpansions = (params?: { limit?: number }) => {
  return useQuery({
    queryKey: ['user-expansions', params],
    queryFn: () => apiClient.getUserExpansions(params),
    staleTime: 30 * 1000,
  });
};

export const useExpansionById = (expansionId: string, enabled: boolean = true) => {
  return useQuery({
    queryKey: ['expansion', expansionId],
    queryFn: () => apiClient.getExpansionById(expansionId),
    enabled: enabled && !!expansionId,
    staleTime: 5 * 60 * 1000,
  });
};

export const useRequestBatchExpansions = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requests: Array<{
      sphereId?: string;
      momentIds?: string[];
      expansionType: 'summarize' | 'extract_themes' | 'identify_patterns' | 'find_connections';
      timeRange?: {
        startDate: string;
        endDate: string;
      };
      priority?: 'high' | 'normal' | 'low';
    }>) => apiClient.requestBatchExpansions(requests),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user-expansions'] });
    },
  });
};

