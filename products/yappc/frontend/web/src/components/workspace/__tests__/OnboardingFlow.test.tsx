import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockNavigate = vi.fn();
const mockMutateAsync = vi.fn();
const mockSuggestWorkspace = vi.fn();
const mockSuggestProject = vi.fn();
const mockSetCurrentWorkspaceId = vi.fn();

vi.mock('react-router', () => ({
  useNavigate: () => mockNavigate,
}), { virtual: true });

vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal<typeof import('jotai')>();
  return {
    ...actual,
    useSetAtom: () => mockSetCurrentWorkspaceId,
  };
});

vi.mock('@/hooks/useWorkspaceData', () => ({
  useCreateWorkspace: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
  useNameSuggestions: () => ({
    suggestWorkspace: mockSuggestWorkspace,
    suggestProject: mockSuggestProject,
  }),
}));

import { OnboardingFlow } from '../OnboardingFlow';

describe('OnboardingFlow', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    const storage = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value);
      },
      removeItem: (key: string) => {
        storage.delete(key);
      },
      clear: () => {
        storage.clear();
      },
    });

    mockSuggestWorkspace.mockResolvedValue('Starter Workspace');
    mockSuggestProject.mockResolvedValue('Starter Project');
    mockMutateAsync.mockResolvedValue({ id: 'ws-123', name: 'Starter Workspace' });
  });

  it('persists starter project metadata and personas through the workspace bootstrap flow', async () => {
    const user = userEvent.setup();

    render(<OnboardingFlow redirectTo="/workspaces" />);

    await user.click(screen.getByRole('button', { name: /let's go/i }));

    await screen.findByDisplayValue('Starter Workspace');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await screen.findByDisplayValue('Starter Project');
    await user.click(screen.getByRole('button', { name: /mobile app/i }));
    const projectNameInput = screen.getByPlaceholderText('My First Project');
    fireEvent.change(projectNameInput, { target: { value: 'Launch Companion' } });
    await user.click(screen.getByRole('button', { name: /create & finish/i }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({
        name: 'Starter Workspace',
        createDefaultProject: true,
        personaSelections: ['developer'],
        defaultProject: {
          name: 'Launch Companion',
          type: 'MOBILE',
        },
      });
    });

    expect(mockSetCurrentWorkspaceId).toHaveBeenCalledWith('ws-123');
    expect(localStorage.getItem('onboarding_complete')).toBe('true');
    expect(localStorage.getItem('yappc_active_personas')).toBe(JSON.stringify(['developer']));
    expect(localStorage.getItem('yappc_primary_persona')).toBe('developer');
  });

  it('does not mark onboarding complete when workspace creation fails', async () => {
    const user = userEvent.setup();
    mockMutateAsync.mockRejectedValueOnce(new Error('service unavailable'));

    render(<OnboardingFlow redirectTo="/workspaces" />);

    await user.click(screen.getByRole('button', { name: /let's go/i }));
    await screen.findByDisplayValue('Starter Workspace');
    await user.click(screen.getByRole('button', { name: /continue/i }));
    await screen.findByDisplayValue('Starter Project');
    await user.click(screen.getByRole('button', { name: /create & finish/i }));

    expect(
      await screen.findByText('We could not finish onboarding because workspace setup did not complete. No data was marked as complete.')
    ).toBeDefined();
    expect(localStorage.getItem('onboarding_complete')).toBeNull();
    expect(mockSetCurrentWorkspaceId).not.toHaveBeenCalled();
  });
});