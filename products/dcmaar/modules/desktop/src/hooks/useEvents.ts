import { useQuery } from '@tanstack/react-query';
import { eventsClient } from '../services/mockClient';

export const EVENTS_QUERY_KEY = ['events', 'all'] as const;

export const useEventsData = () =>
  useQuery({
    queryKey: EVENTS_QUERY_KEY,
    queryFn: eventsClient.fetchEvents,
    staleTime: 30_000,
  });
