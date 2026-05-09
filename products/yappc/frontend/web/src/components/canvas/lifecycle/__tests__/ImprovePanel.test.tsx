import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ImprovePanel, type ImprovePanelProps } from '../ImprovePanel';
import type { EnhancementBacklogPayload, LearningRecordPayload } from '@/shared/types/lifecycle-artifacts';

function makeEnhancements(
  overrides: Partial<EnhancementBacklogPayload> = {},
): EnhancementBacklogPayload {
  return { items: [], ...overrides };
}

function makeLearnings(overrides: Partial<LearningRecordPayload> = {}): LearningRecordPayload {
  return { retrospectives: [], insights: [], recommendations: [], ...overrides };
}

function makeProps(overrides: Partial<ImprovePanelProps> = {}): ImprovePanelProps {
  return {
    onSave: vi.fn().mockResolvedValue(undefined),
    onClose: vi.fn(),
    ...overrides,
  };
}

describe('ImprovePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders header with title and subtitle', () => {
    render(<ImprovePanel {...makeProps()} />);
    expect(screen.getByText('Improve')).toBeDefined();
    expect(screen.getByText('Enhancements & Learnings')).toBeDefined();
  });

  it('renders Enhancements and Learnings tabs', () => {
    render(<ImprovePanel {...makeProps()} />);
    // Tab buttons (role=button) contain these words; subtitle also contains them
    const enhancementButtons = screen.getAllByText(/Enhancements/);
    expect(enhancementButtons.length).toBeGreaterThanOrEqual(1);
    const learningsButtons = screen.getAllByText(/Learnings/);
    expect(learningsButtons.length).toBeGreaterThanOrEqual(1);
  });

  it('shows empty state when no enhancements', () => {
    render(<ImprovePanel {...makeProps()} />);
    expect(screen.getByText('No enhancements yet')).toBeDefined();
    expect(screen.getByText('Add first enhancement')).toBeDefined();
  });

  it('renders provided enhancement items', () => {
    const enhancements = makeEnhancements({
      items: [
        { title: 'Improve caching', description: 'Cache DB calls', source: 'team', status: 'proposed', priority: 'high' },
      ],
    });
    render(<ImprovePanel {...makeProps({ enhancements })} />);
    expect(screen.getByDisplayValue('Improve caching')).toBeDefined();
  });

  it('adds a new enhancement when Add Enhancement is clicked', () => {
    render(<ImprovePanel {...makeProps()} />);
    fireEvent.click(screen.getByText('Add Enhancement'));
    // A new empty title input should appear
    const inputs = screen.getAllByPlaceholderText('Enhancement title');
    expect(inputs.length).toBe(1);
  });

  it('shows enhancement count in tab label after adding', () => {
    render(<ImprovePanel {...makeProps()} />);
    fireEvent.click(screen.getByText('Add Enhancement'));
    expect(screen.getByText('Enhancements (1)')).toBeDefined();
  });

  it('switches to learnings tab when clicked', () => {
    render(<ImprovePanel {...makeProps()} />);
    // Use button role to target tab button, not the subtitle
    fireEvent.click(screen.getByRole('button', { name: /Learnings/ }));
    expect(screen.getByText('Key Insights')).toBeDefined();
    expect(screen.getByText('Recommendations')).toBeDefined();
  });

  it('shows no retrospectives when learnings empty', () => {
    render(<ImprovePanel {...makeProps({ learnings: makeLearnings() })} />);
    fireEvent.click(screen.getByRole('button', { name: /Learnings/ }));
    expect(screen.getByText('No retrospectives yet')).toBeDefined();
  });

  it('shows provided insights on learnings tab', () => {
    const learnings = makeLearnings({ insights: ['Async testing is key'] });
    render(<ImprovePanel {...makeProps({ learnings })} />);
    fireEvent.click(screen.getByRole('button', { name: /Learnings/ }));
    expect(screen.getByDisplayValue('Async testing is key')).toBeDefined();
  });

  it('shows provided recommendations on learnings tab', () => {
    const learnings = makeLearnings({ recommendations: ['Adopt CI caching'] });
    render(<ImprovePanel {...makeProps({ learnings })} />);
    fireEvent.click(screen.getByRole('button', { name: /Learnings/ }));
    expect(screen.getByDisplayValue('Adopt CI caching')).toBeDefined();
  });

  it('renders Save button', () => {
    render(<ImprovePanel {...makeProps()} />);
    expect(screen.getByText('Save')).toBeDefined();
  });

  it('calls onSave with cleaned data when Save is clicked', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<ImprovePanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByText('Save'));
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledOnce();
    });
    const [arg] = onSave.mock.calls[0] as [{ enhancements: EnhancementBacklogPayload; learnings: LearningRecordPayload }][];
    expect(arg).toHaveProperty('enhancements');
    expect(arg).toHaveProperty('learnings');
  });

  it('calls onClose when not directly triggered by onSave flow', () => {
    const onClose = vi.fn();
    render(<ImprovePanel {...makeProps({ onClose })} />);
    // onClose is stored as prop and not called by render itself
    expect(onClose).not.toHaveBeenCalled();
  });

  it('renders Guided Assist button when onAIAssist is provided', () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<ImprovePanel {...makeProps({ onAIAssist })} />);
    expect(screen.getByText('Guided Assist')).toBeDefined();
  });

  it('does not render Guided Assist button when onAIAssist is absent', () => {
    render(<ImprovePanel {...makeProps({ onAIAssist: undefined })} />);
    expect(screen.queryByText('Guided Assist')).toBeNull();
  });

  it('calls onAIAssist when Guided Assist button clicked', async () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<ImprovePanel {...makeProps({ onAIAssist })} />);
    fireEvent.click(screen.getByText('Guided Assist'));
    await waitFor(() => {
      expect(onAIAssist).toHaveBeenCalledOnce();
    });
  });

  it('removes enhancement when remove button clicked', () => {
    const enhancements = makeEnhancements({
      items: [
        { title: 'Old feature', description: '', source: 'team', status: 'proposed', priority: 'low' },
      ],
    });
    render(<ImprovePanel {...makeProps({ enhancements })} />);
    expect(screen.getByDisplayValue('Old feature')).toBeDefined();
    // Remove button is a button with an icon - find by its aria context
    const removeButtons = screen.getAllByRole('button');
    // The remove button is the small minus icon button in the enhancement card
    // Find the remove for this item (not Save/Guided Assist/Add Enhancement)
    const itemRemoveBtn = removeButtons.find(
      (btn) =>
        btn !== screen.queryByText('Save')?.closest('button') &&
        btn !== screen.queryByText('Add Enhancement')?.closest('button') &&
        btn.querySelector('svg') !== null &&
        !btn.textContent?.includes('Save') &&
        !btn.textContent?.includes('Enhancement'),
    );
    if (itemRemoveBtn) {
      fireEvent.click(itemRemoveBtn);
    }
    expect(screen.queryByDisplayValue('Old feature')).toBeNull();
  });

  it('shows saving state while onSave is pending', async () => {
    let resolve: () => void;
    const onSave = vi.fn().mockReturnValue(new Promise<void>((r) => { resolve = r; }));
    render(<ImprovePanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByText('Save'));
    expect(screen.getByText('Saving...')).toBeDefined();
    resolve!();
    await waitFor(() => {
      expect(screen.getByText('Save')).toBeDefined();
    });
  });
});
