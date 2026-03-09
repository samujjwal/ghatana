import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import api from './api';
import { DASHBOARD_QUERY_KEY } from '../hooks/useDashboardData';

export const useConnectionMonitor = () => {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!queryClient) return; // Skip if no QueryClient available
    
    // Check connections every 10 seconds
    const interval = setInterval(() => {
      queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEY });
    }, 10000);

    return () => clearInterval(interval);
  }, [queryClient]);

  const checkDaemonConnection = async () => {
    try {
      await api.get('/status/daemon');
      return true;
    } catch {
      return false;
    }
  };

  const checkExtensionConnection = async () => {
    try {
      await api.get('/status/extension');
      return true;
    } catch {
      return false;
    }
  };

  return {
    checkDaemonConnection,
    checkExtensionConnection,
  };
};
