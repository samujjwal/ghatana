import { ApolloProvider as BaseApolloProvider } from '@apollo/client/react';
import React from 'react';

import { getGraphQLClient } from 'yappc-core/api';

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
    <BaseApolloProvider client={getGraphQLClient()}>
      {children}
    </BaseApolloProvider>
  );
}

export default ApolloProvider;
