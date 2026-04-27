import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThreatModelPanel, type ThreatModelPanelProps } from '../ThreatModelPanel';
import type { ThreatModelPayload } from '@/shared/types/lifecycle-artifacts';

function makeData(overrides: Partial<ThreatModelPayload> = {}): ThreatModelPayload {
  return {
    assets: [{ name: '', description: '' }],
    actors: [{ name: '', description: '', type: 'external' }],
    threats: [],
    mitigations: [],
    residualRisk: '',
    ...overrides,
  };
}

function makeProps(overrides: Partial<ThreatModelPanelProps> = {}): ThreatModelPanelProps {
  return {
    onSave: vi.fn().mockResolvedValue(undefined),
    onClose: vi.fn(),
    ...overrides,
  };
}

describe('ThreatModelPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders header with title and subtitle', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    expect(screen.getByText('Threat Model')).toBeDefined();
    expect(screen.getByText('STRIDE-based security analysis')).toBeDefined();
  });

  it('renders Assets & Actors, Threats and Mitigations tabs', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    expect(screen.getByText('Assets & Actors')).toBeDefined();
    expect(screen.getByText('Threats (0)')).toBeDefined();
    expect(screen.getByText('Mitigations (0)')).toBeDefined();
  });

  it('shows assets section heading on default tab', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    expect(screen.getByText('Assets (What to protect)')).toBeDefined();
    expect(screen.getByText('Threat Actors')).toBeDefined();
  });

  it('renders pre-filled asset name', () => {
    const data = makeData({
      assets: [{ name: 'User Database', description: 'Stores PII' }],
    });
    render(<ThreatModelPanel {...makeProps({ data })} />);
    expect(screen.getByDisplayValue('User Database')).toBeDefined();
    expect(screen.getByDisplayValue('Stores PII')).toBeDefined();
  });

  it('adds a new asset row when Add asset is clicked', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    const initialNames = screen.getAllByPlaceholderText('Asset name');
    fireEvent.click(screen.getByText('Add asset'));
    const afterNames = screen.getAllByPlaceholderText('Asset name');
    expect(afterNames.length).toBe(initialNames.length + 1);
  });

  it('adds a new actor row when Add actor is clicked', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    const initial = screen.getAllByPlaceholderText('Actor name');
    fireEvent.click(screen.getByText('Add actor'));
    const after = screen.getAllByPlaceholderText('Actor name');
    expect(after.length).toBe(initial.length + 1);
  });

  it('switches to threats tab and shows STRIDE legend', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    fireEvent.click(screen.getByRole('button', { name: /Threats/ }));
    // STRIDE abbreviations
    expect(screen.getByText('SP')).toBeDefined();
    expect(screen.getByText('TA')).toBeDefined();
    expect(screen.getByText('EP')).toBeDefined();
  });

  it('shows empty threats message on threats tab', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    fireEvent.click(screen.getByRole('button', { name: /Threats/ }));
    expect(screen.getByText('No threats yet')).toBeDefined();
  });

  it('renders provided threats on threats tab', () => {
    const data = makeData({
      threats: [
        { asset: 'Auth Service', category: 'spoofing', description: 'Credential stuffing', severity: 'high' },
      ],
    });
    render(<ThreatModelPanel {...makeProps({ data })} />);
    fireEvent.click(screen.getByRole('button', { name: /Threats/ }));
    expect(screen.getByDisplayValue('Credential stuffing')).toBeDefined();
  });

  it('shows threat count in tab label', () => {
    const data = makeData({
      threats: [
        { asset: 'API', category: 'tampering', description: 'SQL injection', severity: 'critical' },
        { asset: 'DB', category: 'repudiation', description: 'Log tampering', severity: 'medium' },
      ],
    });
    render(<ThreatModelPanel {...makeProps({ data })} />);
    expect(screen.getByText('Threats (2)')).toBeDefined();
  });

  it('adds a new threat on threats tab', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    fireEvent.click(screen.getByRole('button', { name: /Threats/ }));
    fireEvent.click(screen.getByText('Add Threat'));
    const descTextareas = screen.getAllByPlaceholderText('Describe the threat...');
    expect(descTextareas.length).toBe(1);
  });

  it('switches to mitigations tab', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    fireEvent.click(screen.getByText('Mitigations (0)'));
    // Mitigations empty state is shown
    expect(screen.getByText('No mitigations yet')).toBeDefined();
  });

  it('shows mitigations count when threats have mitigations', () => {
    const data = makeData({
      mitigations: [
        { threat: 'Credential stuffing', control: 'Rate limiting', status: 'planned' },
      ],
    });
    render(<ThreatModelPanel {...makeProps({ data })} />);
    expect(screen.getByText('Mitigations (1)')).toBeDefined();
  });

  it('renders residual risk textarea on mitigations tab', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    fireEvent.click(screen.getByText('Mitigations (0)'));
    expect(screen.getByPlaceholderText('Describe remaining risks after mitigations are applied...')).toBeDefined();
  });

  it('renders Save button', () => {
    render(<ThreatModelPanel {...makeProps()} />);
    expect(screen.getByRole('button', { name: /Save/ })).toBeDefined();
  });

  it('calls onSave with cleaned model when Save is clicked', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<ThreatModelPanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByRole('button', { name: /Save/ }));
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledOnce();
    });
    const [arg] = onSave.mock.calls[0] as [ThreatModelPayload][];
    expect(arg).toHaveProperty('assets');
    expect(arg).toHaveProperty('threats');
    expect(arg).toHaveProperty('mitigations');
  });

  it('renders AI Assist button when onAIAssist provided', () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<ThreatModelPanel {...makeProps({ onAIAssist })} />);
    expect(screen.getByText('AI Assist')).toBeDefined();
  });

  it('does not render AI Assist when onAIAssist absent', () => {
    render(<ThreatModelPanel {...makeProps({ onAIAssist: undefined })} />);
    expect(screen.queryByText('AI Assist')).toBeNull();
  });

  it('calls onAIAssist when button clicked', async () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<ThreatModelPanel {...makeProps({ onAIAssist })} />);
    fireEvent.click(screen.getByText('AI Assist'));
    await waitFor(() => {
      expect(onAIAssist).toHaveBeenCalledOnce();
    });
  });

  it('shows Saving... while save is pending', async () => {
    let resolve: () => void;
    const onSave = vi.fn().mockReturnValue(new Promise<void>((r) => { resolve = r; }));
    render(<ThreatModelPanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByRole('button', { name: /Save/ }));
    expect(screen.getByText('Saving...')).toBeDefined();
    resolve!();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Save/ })).toBeDefined();
    });
  });
});
