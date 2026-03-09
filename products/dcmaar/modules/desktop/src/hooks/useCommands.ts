import { useQuery } from '@tanstack/react-query';
import { commandsClient } from '../services/mockClient';

export const COMMANDS_QUERY_KEY = ['commands', 'catalogue'] as const;

export const useCommandsData = () =>
  useQuery({
    queryKey: COMMANDS_QUERY_KEY,
    queryFn: commandsClient.fetchCommands,
    staleTime: 60_000,
  });
