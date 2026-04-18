import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { GlobalSearch } from '../../components/common/GlobalSearch';
import { TestWrapper } from '../test-utils/wrapper';
import SessionBootstrap from '../../lib/auth/session';
import { TEST_TENANT_ID } from '../test-utils/tenants';

vi.mock('../../lib/api/collections', () => ({
  collectionsApi: {
    list: vi.fn().mockResolvedValue({ items: [] }),
  },
}));

vi.mock('../../lib/api/workflows', () => ({
  workflowsApi: {
    list: vi.fn().mockResolvedValue({ items: [] }),
  },
}));

describe('GlobalSearch', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
  });

  it('hides operator and admin quick navigation in primary-user mode', async () => {
    SessionBootstrap.setShellRole('primary-user');

    render(<GlobalSearch isOpen={true} onClose={() => {}} />, { wrapper: TestWrapper });

    expect(await screen.findByText('Home')).toBeInTheDocument();
    expect(screen.queryByText('Insights')).not.toBeInTheDocument();
    expect(screen.queryByText('Events')).not.toBeInTheDocument();
    expect(screen.queryByText('Settings')).not.toBeInTheDocument();
  });

  it('reveals operator and admin quick navigation only for the matching shell role', async () => {
    SessionBootstrap.setShellRole('admin');

    render(<GlobalSearch isOpen={true} onClose={() => {}} />, { wrapper: TestWrapper });

    expect(await screen.findByText('Insights')).toBeInTheDocument();
    expect(screen.getByText('Trust')).toBeInTheDocument();
    expect(screen.getByText('Events')).toBeInTheDocument();
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });
});