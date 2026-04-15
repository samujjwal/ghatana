/**
 * Tests for the Plugins Page.
 *
 * Covers the PluginsPage component — plugin browsing (marketplace),
 * installed tab, and upload tab rendering.
 *
 * @doc.type test
 * @doc.purpose RTL tests for PluginsPage rendering and interactions
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import type { Plugin } from '../../api/plugin.service';

// ── Mocks ─────────────────────────────────────────────────────────────────────

vi.mock('react-router', async (importOriginal) => {
    const actual = await importOriginal<typeof import('react-router')>();
    return {
        ...actual,
        useNavigate: vi.fn(() => vi.fn()),
    };
});

vi.mock('../../api/plugin.service', () => ({
    pluginService: {
        getInstalledPlugins: vi.fn(),
        enablePlugin: vi.fn(),
        disablePlugin: vi.fn(),
    },
}));

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
        post: vi.fn().mockResolvedValue({}),
        delete: vi.fn().mockResolvedValue(undefined),
    },
}));

import { PluginsPage } from '../../pages/PluginsPage';
import { pluginService } from '../../api/plugin.service';

const SAMPLE_PLUGINS: Plugin[] = [
    {
        id: 'plugin-1',
        metadata: {
            id: 'plugin-1',
            name: 'Alpha Connector',
            version: '1.2.0',
            author: 'Ghatana',
            description: 'Bundled connector plugin for CRM sync',
            category: 'connector',
            license: 'Bundled',
            tags: ['crm', 'bundled'],
        },
        status: 'active',
        installedAt: '2026-04-15T08:00:00Z',
        updatedAt: '2026-04-15T08:10:00Z',
        capabilities: [],
    },
    {
        id: 'plugin-2',
        metadata: {
            id: 'plugin-2',
            name: 'PII Guard',
            version: '2.0.0',
            author: 'Ghatana',
            description: 'Bundled governance plugin for privacy checks',
            category: 'governance',
            license: 'Bundled',
            tags: ['pii', 'privacy'],
        },
        status: 'inactive',
        installedAt: '2026-04-15T08:20:00Z',
        updatedAt: '2026-04-15T08:30:00Z',
        capabilities: [],
    },
];

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('PluginPage — PluginsPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(pluginService.getInstalledPlugins).mockResolvedValue(SAMPLE_PLUGINS);
        vi.mocked(pluginService.enablePlugin).mockResolvedValue(SAMPLE_PLUGINS[0]);
        vi.mocked(pluginService.disablePlugin).mockResolvedValue(SAMPLE_PLUGINS[1]);
    });

    it('renders canonical bundled plugin inventory with live counts', async () => {
        render(<PluginsPage />, { wrapper: TestWrapper });

        expect(await screen.findByText('Alpha Connector')).toBeInTheDocument();
        expect(screen.getByText('PII Guard')).toBeInTheDocument();
        expect(within(screen.getByText('Total').closest('div')?.parentElement?.parentElement as HTMLElement).getByText('2')).toBeInTheDocument();
        expect(screen.getAllByText('1').length).toBeGreaterThanOrEqual(2);
        expect(screen.getByText(/monitor the bundled plugins shipped with the current launcher build/i)).toBeInTheDocument();
    });

    it('filters installed plugins by search, category, and status then clears filters', async () => {
        render(<PluginsPage />, { wrapper: TestWrapper });

        await screen.findByText('Alpha Connector');

        fireEvent.change(screen.getByPlaceholderText(/search plugins/i), {
            target: { value: 'alpha' },
        });

        await waitFor(() => {
            expect(screen.getByText('Alpha Connector')).toBeInTheDocument();
            expect(screen.queryByText('PII Guard')).not.toBeInTheDocument();
        });

        fireEvent.change(screen.getByDisplayValue('All'), {
            target: { value: 'governance' },
        });
        fireEvent.change(screen.getByDisplayValue('All Status'), {
            target: { value: 'inactive' },
        });
        fireEvent.change(screen.getByPlaceholderText(/search plugins/i), {
            target: { value: '' },
        });

        await waitFor(() => {
            expect(screen.getByText('PII Guard')).toBeInTheDocument();
            expect(screen.queryByText('Alpha Connector')).not.toBeInTheDocument();
        });

        fireEvent.click(screen.getByRole('button', { name: /clear all filters/i }));

        await waitFor(() => {
            expect(screen.getByText('Alpha Connector')).toBeInTheDocument();
            expect(screen.getByText('PII Guard')).toBeInTheDocument();
        });
    });

    it('shows honest catalog and delivery boundary guidance', async () => {
        render(<PluginsPage />, { wrapper: TestWrapper });

        await screen.findByText('Alpha Connector');

        fireEvent.click(screen.getByRole('button', { name: /catalog boundary/i }));
        expect(screen.getByText(/marketplace browsing, runtime installation, and custom uploads are intentionally unavailable/i)).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /deployment/i }));
        expect(screen.getByText(/publish a new Data Cloud server build that includes the updated bundled plugin artifact/i)).toBeInTheDocument();
    });

    it('invokes the canonical runtime toggle actions for installed plugins', async () => {
        render(<PluginsPage />, { wrapper: TestWrapper });

        await screen.findByText('Alpha Connector');

        fireEvent.click(screen.getByRole('button', { name: /disable alpha connector/i }));
        fireEvent.click(screen.getByRole('button', { name: /enable pii guard/i }));

        await waitFor(() => {
            expect(pluginService.disablePlugin).toHaveBeenCalledWith('plugin-1');
            expect(pluginService.enablePlugin).toHaveBeenCalledWith('plugin-2');
        });
    });
});
