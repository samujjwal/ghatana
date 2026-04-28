import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom';
import { LiveStatusBanner } from '../LiveStatusBanner';

describe('LiveStatusBanner', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders nothing when status is IDLE', () => {
    const { container } = render(<LiveStatusBanner status="IDLE" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a running banner', () => {
    render(<LiveStatusBanner status="RUNNING" label="Generating code…" />);
    const banner = screen.getByRole('status');
    expect(banner).toBeInTheDocument();
    expect(banner).toHaveTextContent('Generating code…');
  });

  it('renders a failed banner with error message', () => {
    render(
      <LiveStatusBanner status="FAILED" label="Operation failed" errorMessage="Timeout exceeded" />,
    );
    const banner = screen.getByRole('status');
    expect(banner).toHaveTextContent('Timeout exceeded');
  });

  it('renders a completed banner', () => {
    vi.useFakeTimers();
    render(<LiveStatusBanner status="COMPLETED" autoDismissMs={0} />);
    expect(screen.getByRole('status')).toHaveTextContent('Operation completed');
  });

  it('auto-dismisses after completion delay', async () => {
    vi.useFakeTimers();
    render(<LiveStatusBanner status="COMPLETED" autoDismissMs={3000} />);
    expect(screen.getByRole('status')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(3001);
    });

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('calls onDismiss when dismiss button is clicked', async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    render(<LiveStatusBanner status="FAILED" onDismiss={onDismiss} />);
    await user.click(screen.getByRole('button', { name: /dismiss/i }));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it('does not render dismiss button when onDismiss is not provided', () => {
    render(<LiveStatusBanner status="RUNNING" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
