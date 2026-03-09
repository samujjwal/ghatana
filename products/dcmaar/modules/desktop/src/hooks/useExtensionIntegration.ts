/**
 * React hook for Extension Service integration
 *
 * Provides real-time access to browser extension data including tabs,
 * network requests, performance metrics, and events.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, useCallback } from 'react';
import { ExtensionService, BrowserEvent, NetworkRequest, PerformanceMetrics } from '../services/extensionService';

// Singleton instance
let extensionService: ExtensionService | null = null;

const getExtensionService = (): ExtensionService => {
  if (!extensionService) {
    // Default configuration - can be overridden via environment variables
    extensionService = new ExtensionService({
      wsPort: parseInt(import.meta.env.VITE_EXTENSION_WS_PORT || '9001'),
      reconnectInterval: 5000,
      maxReconnectAttempts: 10,
    });
  }
  return extensionService;
};

/**
 * Hook to manage extension connection
 */
export const useExtensionConnection = () => {
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const service = getExtensionService();

    const connect = async () => {
      try {
        await service.connect();
        setIsConnected(true);
        setError(null);
      } catch (err) {
        setIsConnected(false);
        setError(err instanceof Error ? err : new Error('Connection failed'));
      }
    };

    connect();

    return () => {
      service.disconnect();
      setIsConnected(false);
    };
  }, []);

  return { isConnected, error };
};

/**
 * Hook to get browser tabs
 */
export const useExtensionTabs = () => {
  return useQuery({
    queryKey: ['extension', 'tabs'],
    queryFn: () => getExtensionService().getTabs(),
    staleTime: 5_000,
    refetchInterval: 10_000, // Refresh every 10 seconds
  });
};

/**
 * Hook to get network requests
 */
export const useExtensionNetworkRequests = (filter?: any) => {
  return useQuery({
    queryKey: ['extension', 'network', filter],
    queryFn: () => getExtensionService().getNetworkRequests(filter as any),
    staleTime: 5_000,
  });
};

/**
 * Hook to get performance metrics
 */
export const useExtensionPerformance = () => {
  return useQuery({
    queryKey: ['extension', 'performance'],
    queryFn: () => getExtensionService().getPerformanceMetrics(),
    staleTime: 5_000,
    refetchInterval: 15_000, // Refresh every 15 seconds
  });
};

/**
 * Hook to capture screenshot
 */
export const useCaptureScreenshot = () => {
  return useMutation({
    mutationFn: () => getExtensionService().captureScreenshot(),
  });
};

/**
 * Hook to start monitoring a tab
 */
export const useStartMonitoring = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (tabId: number) => getExtensionService().startMonitoring(tabId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['extension', 'tabs'] });
    },
  });
};

/**
 * Hook to stop monitoring a tab
 */
export const useStopMonitoring = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (tabId: number) => getExtensionService().stopMonitoring(tabId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['extension', 'tabs'] });
    },
  });
};

/**
 * Hook to execute script in a tab
 */
export const useExecuteScript = () => {
  return useMutation({
    mutationFn: ({ tabId, code }: { tabId: number; code: string }) =>
      getExtensionService().executeScript(tabId, code),
  });
};

/**
 * Hook for real-time browser events stream
 */
export const useExtensionEventsStream = (eventType?: string) => {
  const [events, setEvents] = useState<BrowserEvent[]>([]);
  const [isConnected, setIsConnected] = useState(false);

  const addEvent = useCallback(
    (event: BrowserEvent) => {
      if (!eventType || event.type === eventType) {
        setEvents((prev) => [event, ...prev].slice(0, 200)); // Keep last 200 events
      }
    },
    [eventType]
  );

  useEffect(() => {
    const service = getExtensionService();

    // Set up event listener
    const unsubscribe = service.on('*', addEvent);
    setIsConnected(true);

    return () => {
      unsubscribe();
      setIsConnected(false);
    };
  }, [addEvent]);

  return { events, isConnected };
};

/**
 * Hook for specific event type
 */
export const useExtensionEvent = (eventType: string, callback: (event: BrowserEvent) => void) => {
  useEffect(() => {
    const service = getExtensionService();
    const unsubscribe = service.on(eventType, callback);
    return unsubscribe;
  }, [eventType, callback]);
};

/**
 * Hook to get page load events
 */
export const usePageLoadEvents = () => {
  const [pageLoads, setPageLoads] = useState<BrowserEvent[]>([]);

  useExtensionEvent('pageLoad', useCallback((event: BrowserEvent) => {
    setPageLoads((prev) => [event, ...prev].slice(0, 50));
  }, []));

  return pageLoads;
};

/**
 * Hook to get navigation events
 */
export const useNavigationEvents = () => {
  const [navigations, setNavigations] = useState<BrowserEvent[]>([]);

  useExtensionEvent('navigation', useCallback((event: BrowserEvent) => {
    setNavigations((prev) => [event, ...prev].slice(0, 50));
  }, []));

  return navigations;
};

/**
 * Hook to get error events
 */
export const useErrorEvents = () => {
  const [errors, setErrors] = useState<BrowserEvent[]>([]);

  useExtensionEvent('error', useCallback((event: BrowserEvent) => {
    setErrors((prev) => [event, ...prev].slice(0, 50));
  }, []));

  return errors;
};

/**
 * Hook to get network error events
 */
export const useNetworkErrorEvents = () => {
  const [networkErrors, setNetworkErrors] = useState<BrowserEvent[]>([]);

  useExtensionEvent('networkError', useCallback((event: BrowserEvent) => {
    setNetworkErrors((prev) => [event, ...prev].slice(0, 50));
  }, []));

  return networkErrors;
};

/**
 * Export the service instance for direct access if needed
 */
export const getExtension = getExtensionService;
