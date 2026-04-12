/**
 * @fileoverview Tests for the multi-mode canvas React API.
 *
 * Covers: mode switching, tool selection, zoom controls, viewport isolation,
 * read-only enforcement, and controlled/uncontrolled usage patterns.
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import {
  MultiModeCanvas,
  CanvasModeGate,
  CanvasZoomControls,
  CanvasModeSwitcher,
  useMultiModeCanvas,
  useCanvasMode,
  useActiveTool,
  useCanvasZoom,
  type CanvasMode,
} from '../multi-mode.js';

// ── Helper: consumer that reads context ──────────────────────────────────────

function ContextReader(): React.JSX.Element {
  const ctx = useMultiModeCanvas();
  return (
    <div>
      <span data-testid="mode">{ctx.mode}</span>
      <span data-testid="tool">{ctx.tool}</span>
      <span data-testid="zoom">{ctx.zoom}</span>
      <span data-testid="readonly">{String(ctx.readOnly)}</span>
      <span data-testid="transitioning">{String(ctx.transitioning)}</span>
    </div>
  );
}

function renderCanvas(props: Partial<React.ComponentProps<typeof MultiModeCanvas>> = {}) {
  return render(
    <MultiModeCanvas {...props}>
      <ContextReader />
    </MultiModeCanvas>,
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('MultiModeCanvas — uncontrolled', () => {
  it('starts in the specified initialMode', () => {
    renderCanvas({ initialMode: 'graph' });
    expect(screen.getByTestId('mode').textContent).toBe('graph');
  });

  it('defaults to freeform mode', () => {
    renderCanvas();
    expect(screen.getByTestId('mode').textContent).toBe('freeform');
  });

  it('defaults to pencil tool in freeform mode', () => {
    renderCanvas({ initialMode: 'freeform' });
    expect(screen.getByTestId('tool').textContent).toBe('pencil');
  });

  it('defaults to select tool in graph mode', () => {
    renderCanvas({ initialMode: 'graph' });
    expect(screen.getByTestId('tool').textContent).toBe('select');
  });

  it('defaults to hand tool in read-only mode', () => {
    renderCanvas({ initialMode: 'read-only' });
    expect(screen.getByTestId('tool').textContent).toBe('hand');
  });

  it('defaults zoom to 1', () => {
    renderCanvas();
    expect(screen.getByTestId('zoom').textContent).toBe('1');
  });

  it('sets readOnly correctly', () => {
    renderCanvas({ readOnly: true });
    expect(screen.getByTestId('readonly').textContent).toBe('true');
  });
});

describe('MultiModeCanvas — CanvasModeSwitcher', () => {
  it('renders all mode tabs', () => {
    render(
      <MultiModeCanvas>
        <CanvasModeSwitcher />
      </MultiModeCanvas>,
    );
    expect(screen.getByRole('tab', { name: /freeform/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /graph/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /builder/i })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /view/i })).toBeTruthy();
  });

  it('marks the active mode tab as aria-selected', () => {
    render(
      <MultiModeCanvas initialMode="graph">
        <CanvasModeSwitcher />
      </MultiModeCanvas>,
    );
    const graphTab = screen.getByRole('tab', { name: /graph/i });
    expect(graphTab.getAttribute('aria-selected')).toBe('true');
  });

  it('switches mode when a tab is clicked', () => {
    render(
      <MultiModeCanvas>
        <ContextReader />
        <CanvasModeSwitcher />
      </MultiModeCanvas>,
    );
    fireEvent.click(screen.getByRole('tab', { name: /graph/i }));
    expect(screen.getByTestId('mode').textContent).toBe('graph');
  });

  it('renders only requested modes when availableModes is set', () => {
    render(
      <MultiModeCanvas>
        <CanvasModeSwitcher availableModes={['freeform', 'graph']} />
      </MultiModeCanvas>,
    );
    expect(screen.queryByRole('tab', { name: /builder/i })).toBeNull();
    expect(screen.getByRole('tab', { name: /graph/i })).toBeTruthy();
  });

  it('uses custom modeLabels', () => {
    render(
      <MultiModeCanvas>
        <CanvasModeSwitcher modeLabels={{ graph: 'Pipeline' }} availableModes={['graph']} />
      </MultiModeCanvas>,
    );
    expect(screen.getByRole('tab', { name: /pipeline/i })).toBeTruthy();
  });

  it('calls onModeChange when switching', () => {
    const onModeChange = vi.fn();
    render(
      <MultiModeCanvas onModeChange={onModeChange}>
        <CanvasModeSwitcher />
      </MultiModeCanvas>,
    );
    fireEvent.click(screen.getByRole('tab', { name: /builder/i }));
    expect(onModeChange).toHaveBeenCalledWith('builder');
  });
});

describe('MultiModeCanvas — CanvasZoomControls', () => {
  it('renders zoom controls', () => {
    render(
      <MultiModeCanvas>
        <CanvasZoomControls />
      </MultiModeCanvas>,
    );
    expect(screen.getByRole('toolbar', { name: /zoom/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /zoom in/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /zoom out/i })).toBeTruthy();
  });

  it('displays current zoom as percentage', () => {
    render(
      <MultiModeCanvas>
        <CanvasZoomControls />
      </MultiModeCanvas>,
    );
    expect(screen.getByRole('button', { name: /current zoom 100%/i })).toBeTruthy();
  });

  it('increments zoom on zoom-in click', async () => {
    render(
      <MultiModeCanvas>
        <ContextReader />
        <CanvasZoomControls />
      </MultiModeCanvas>,
    );
    const before = parseFloat(screen.getByTestId('zoom').textContent ?? '1');
    fireEvent.click(screen.getByRole('button', { name: /zoom in/i }));
    const after = parseFloat(screen.getByTestId('zoom').textContent ?? '1');
    expect(after).toBeGreaterThan(before);
  });

  it('decrements zoom on zoom-out click', () => {
    render(
      <MultiModeCanvas>
        <ContextReader />
        <CanvasZoomControls />
      </MultiModeCanvas>,
    );
    const before = parseFloat(screen.getByTestId('zoom').textContent ?? '1');
    fireEvent.click(screen.getByRole('button', { name: /zoom out/i }));
    const after = parseFloat(screen.getByTestId('zoom').textContent ?? '1');
    expect(after).toBeLessThan(before);
  });

  it('resets zoom to 1 on reset click', () => {
    render(
      <MultiModeCanvas>
        <ContextReader />
        <CanvasZoomControls />
      </MultiModeCanvas>,
    );
    fireEvent.click(screen.getByRole('button', { name: /zoom in/i }));
    fireEvent.click(screen.getByRole('button', { name: /current zoom/i }));
    expect(screen.getByTestId('zoom').textContent).toBe('1');
  });

  it('disables zoom buttons in read-only mode', () => {
    render(
      <MultiModeCanvas readOnly>
        <CanvasZoomControls />
      </MultiModeCanvas>,
    );
    expect(screen.getByRole('button', { name: /zoom in/i }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: /zoom out/i }).hasAttribute('disabled')).toBe(true);
  });
});

describe('MultiModeCanvas — CanvasModeGate', () => {
  it('renders children in matching mode', () => {
    render(
      <MultiModeCanvas initialMode="graph">
        <CanvasModeGate modes={['graph']}>
          <span data-testid="gate-content">visible</span>
        </CanvasModeGate>
      </MultiModeCanvas>,
    );
    expect(screen.getByTestId('gate-content')).toBeTruthy();
  });

  it('hides children in non-matching mode', () => {
    render(
      <MultiModeCanvas initialMode="freeform">
        <CanvasModeGate modes={['graph']}>
          <span data-testid="gate-content">hidden</span>
        </CanvasModeGate>
      </MultiModeCanvas>,
    );
    expect(screen.queryByTestId('gate-content')).toBeNull();
  });

  it('renders fallback in non-matching mode', () => {
    render(
      <MultiModeCanvas initialMode="freeform">
        <CanvasModeGate modes={['graph']} fallback={<span data-testid="fallback">fallback</span>}>
          <span data-testid="content">content</span>
        </CanvasModeGate>
      </MultiModeCanvas>,
    );
    expect(screen.getByTestId('fallback')).toBeTruthy();
    expect(screen.queryByTestId('content')).toBeNull();
  });

  it('matches multiple modes', () => {
    render(
      <MultiModeCanvas initialMode="builder">
        <CanvasModeGate modes={['freeform', 'builder', 'graph']}>
          <span data-testid="gate-content">shown</span>
        </CanvasModeGate>
      </MultiModeCanvas>,
    );
    expect(screen.getByTestId('gate-content')).toBeTruthy();
  });
});

describe('MultiModeCanvas — controlled mode', () => {
  it('reflects controlled mode prop', () => {
    const { rerender } = render(
      <MultiModeCanvas mode="freeform">
        <ContextReader />
      </MultiModeCanvas>,
    );
    expect(screen.getByTestId('mode').textContent).toBe('freeform');

    rerender(
      <MultiModeCanvas mode="builder">
        <ContextReader />
      </MultiModeCanvas>,
    );
    expect(screen.getByTestId('mode').textContent).toBe('builder');
  });

  it('calls onModeChange but does not self-update in controlled mode', () => {
    const onModeChange = vi.fn();
    render(
      <MultiModeCanvas mode="freeform" onModeChange={onModeChange}>
        <ContextReader />
        <CanvasModeSwitcher />
      </MultiModeCanvas>,
    );
    fireEvent.click(screen.getByRole('tab', { name: /graph/i }));
    expect(onModeChange).toHaveBeenCalledWith('graph');
    expect(screen.getByTestId('mode').textContent).toBe('freeform');
  });
});

describe('MultiModeCanvas — hook guards', () => {
  it('throws when useMultiModeCanvas is used outside provider', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => {
      render(<ContextReader />);
    }).toThrow('useMultiModeCanvas must be used inside <MultiModeCanvas>');
    consoleError.mockRestore();
  });
});

describe('MultiModeCanvas — viewport isolation per mode', () => {
  it('each mode has independent zoom', () => {
    function ZoomTester(): React.JSX.Element {
      const { zoom, zoomIn, setMode, mode } = useMultiModeCanvas();
      return (
        <div>
          <span data-testid="mode">{mode}</span>
          <span data-testid="zoom">{zoom}</span>
          <button onClick={zoomIn}>+</button>
          <button onClick={() => setMode('graph')}>graph</button>
          <button onClick={() => setMode('freeform')}>freeform</button>
        </div>
      );
    }

    render(
      <MultiModeCanvas initialMode="freeform">
        <ZoomTester />
      </MultiModeCanvas>,
    );

    // Zoom in on freeform
    fireEvent.click(screen.getByText('+'));
    expect(parseFloat(screen.getByTestId('zoom').textContent ?? '1')).toBeGreaterThan(1);

    // Switch to graph — zoom should be at default 1
    fireEvent.click(screen.getByText('graph'));
    expect(screen.getByTestId('zoom').textContent).toBe('1');

    // Switch back to freeform — zoom should be preserved
    fireEvent.click(screen.getByText('freeform'));
    expect(parseFloat(screen.getByTestId('zoom').textContent ?? '1')).toBeGreaterThan(1);
  });
});

describe('useCanvasMode / useActiveTool / useCanvasZoom', () => {
  function HookConsumer(): React.JSX.Element {
    const mode = useCanvasMode();
    const tool = useActiveTool();
    const zoom = useCanvasZoom();
    return (
      <>
        <span data-testid="m">{mode}</span>
        <span data-testid="t">{tool}</span>
        <span data-testid="z">{zoom}</span>
      </>
    );
  }

  it('returns values from context', () => {
    render(
      <MultiModeCanvas initialMode="builder">
        <HookConsumer />
      </MultiModeCanvas>,
    );
    expect(screen.getByTestId('m').textContent).toBe('builder');
    expect(screen.getByTestId('t').textContent).toBe('select');
    expect(screen.getByTestId('z').textContent).toBe('1');
  });
});
