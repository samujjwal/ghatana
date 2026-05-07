import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider } from 'jotai';
import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { describe, expect, it } from 'vitest';
import {
  AI_COLLABORATOR_FOOTER_NOTE,
  AI_COLLABORATOR_BOUNDARY_MESSAGE,
  AI_COLLABORATOR_BOUNDARY_TITLE,
  AI_COLLABORATOR_CONTEXT_HINT,
} from '@/lib/runtime-boundaries';

import {
  AICollaborator,
} from '../AICollaborator';

function Wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return (
    <Provider>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </Provider>
  );
}

describe('AICollaborator', () => {
  it('renders an explicit launcher boundary note instead of calling unsupported recommendation APIs', () => {
    render(<AICollaborator />, { wrapper: Wrapper });

    expect(screen.getByText('AI Collaborator')).toBeDefined();
    expect(screen.queryByText(AI_COLLABORATOR_BOUNDARY_MESSAGE)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '+' }));

    expect(screen.getByText(AI_COLLABORATOR_BOUNDARY_TITLE)).toBeDefined();
    expect(screen.getByText(AI_COLLABORATOR_BOUNDARY_MESSAGE)).toBeDefined();
    expect(screen.getByText(AI_COLLABORATOR_CONTEXT_HINT)).toBeDefined();
    expect(screen.getByText(AI_COLLABORATOR_FOOTER_NOTE)).toBeDefined();
  });
});