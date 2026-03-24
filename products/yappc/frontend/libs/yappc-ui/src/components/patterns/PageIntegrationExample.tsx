/**
 * Page Integration Example
 * 
 * Demonstrates how to integrate pages with backend services.
 * Shows best practices for loading states, error handling, and data fetching.
 * 
 * @module ui/patterns
 */

import React, { useEffect } from 'react';
import { useQuery } from '@apollo/client';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { ErrorBoundary } from '../components/ErrorBoundary';

/**
 * Page Integration Pattern
 * 
 * This example shows the recommended pattern for integrating pages with backend services.
 * 
 * Key Features:
 * - Error boundary for graceful error handling
 * - Loading states with skeleton screens
 * - Empty states for no data scenarios
 * - GraphQL integration with Apollo Client
 * - Optimistic updates support
 * - Accessibility compliance
 * 
 * @example
 * ```tsx
 * // 1. Define your GraphQL query
 * const GET_SPRINTS = gql`
 *   query GetSprints($projectId: ID!) {
 *     sprints(projectId: $projectId) {
 *       id
 *       name
 *       status
 *       startDate
 *       endDate
 *     }
 *   }
 * `;
 * 
 * // 2. Create your page component
 * export const SprintListPage: React.FC = () => {
 *   const { data, loading, error } = useQuery(GET_SPRINTS, {
 *     variables: { projectId: 'project-123' },
 *   });
 * 
 *   if (loading) return <LoadingState variant="skeleton" />;
 *   if (error) throw error; // Caught by ErrorBoundary
 *   if (!data?.sprints?.length) {
 *     return (
 *       <EmptyState
 *         title="No sprints"
 *         description="Create your first sprint to get started."
 *         action={{
 *           label: "Create Sprint",
 *           onClick: () => navigate('/sprints/new'),
 *         }}
 *       />
 *     );
 *   }
 * 
 *   return (
 *     <div>
 *       {data.sprints.map(sprint => (
 *         <SprintCard key={sprint.id} sprint={sprint} />
 *       ))}
 *     </div>
 *   );
 * };
 * 
 * // 3. Wrap with ErrorBoundary in router
 * <Route
 *   path="/sprints"
 *   element={
 *     <ErrorBoundary>
 *       <SprintListPage />
 *     </ErrorBoundary>
 *   }
 * />
 * ```
 */

// Example GraphQL query (commented out to avoid import errors)
// import { gql } from '@apollo/client';
// const GET_EXAMPLE_DATA = gql`
//   query GetExampleData {
//     items {
//       id
//       name
//     }
//   }
// `;

export interface PageIntegrationExampleProps {
  /** Example prop */
  exampleProp?: string;
}

/**
 * Example Page Component
 * 
 * This is a template showing the recommended integration pattern.
 */
export const PageIntegrationExample: React.FC<PageIntegrationExampleProps> = ({
  exampleProp,
}) => {
  // Simulated query (replace with actual useQuery)
  const loading = false;
  const error = null;
  const data = { items: [] };

  useEffect(() => {
    // Component mount logic
    console.log('Page mounted with prop:', exampleProp);
  }, [exampleProp]);

  // Loading state
  if (loading) {
    return <LoadingState variant="skeleton" message="Loading data..." />;
  }

  // Error state (will be caught by ErrorBoundary)
  if (error) {
    throw error;
  }

  // Empty state
  if (!data?.items?.length) {
    return (
      <EmptyState
        title="No data available"
        description="There are no items to display yet."
        action={{
          label: "Create Item",
          onClick: () => console.log('Create item clicked'),
        }}
      />
    );
  }

  // Success state with data
  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-white mb-4">Example Page</h1>
      <div className="space-y-4">
        {data.items.map((item: unknown) => (
          <div key={item.id} className="p-4 bg-zinc-800 rounded-lg">
            {item.name}
          </div>
        ))}
      </div>
    </div>
  );
};

/**
 * Best Practices Checklist
 * 
 * ✅ Wrap page with ErrorBoundary in router
 * ✅ Use LoadingState for loading scenarios
 * ✅ Use EmptyState for no data scenarios
 * ✅ Use GraphQL with Apollo Client for data fetching
 * ✅ Handle all possible states (loading, error, empty, success)
 * ✅ Use optimistic updates for better UX
 * ✅ Add proper TypeScript types
 * ✅ Include accessibility attributes
 * ✅ Use semantic HTML
 * ✅ Follow responsive design patterns
 * ✅ Add proper error messages
 * ✅ Include loading indicators
 * ✅ Test all states
 */

export default PageIntegrationExample;
