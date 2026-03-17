import { ApolloProvider as BaseApolloProvider } from '@apollo/client/react/context';
import { apolloClient } from '@ghatana/yappc-api';
import React from 'react';

/**
 *
 */
interface ApolloProviderProps {
  children: React.ReactNode;
}

/**
 *
 */
export function ApolloProvider({ children }: ApolloProviderProps) {
  return (
    <BaseApolloProvider client={apolloClient}>
      {children}
    </BaseApolloProvider>
  );
}

export default ApolloProvider;
