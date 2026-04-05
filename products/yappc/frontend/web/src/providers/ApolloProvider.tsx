// @ts-nocheck
import { ApolloProvider as BaseApolloProvider } from '@apollo/client/react';
import { graphqlClient } from '../lib/api-client';
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
    <BaseApolloProvider client={graphqlClient}>
      {children}
    </BaseApolloProvider>
  );
}

export default ApolloProvider;
