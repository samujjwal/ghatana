/**
 * @file Avatar.test.tsx
 * Unit tests for the Avatar atom component.
 *
 * Covers image rendering, fallback initials, size variants, shape variants,
 * status indicators, and accessibility attributes.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Avatar } from '../Avatar';

describe('Avatar', () => {
  // ── Image rendering ───────────────────────────────────────────────────────

  describe('Image rendering', () => {
    it('renders an img tag when src is provided', () => {
      render(<Avatar src="https://example.com/avatar.png" alt="User avatar" />);
      const img = screen.getByRole('img');
      expect(img).toBeInTheDocument();
      expect(img).toHaveAttribute('src', 'https://example.com/avatar.png');
    });

    it('applies alt text to the rendered image', () => {
      render(<Avatar src="https://example.com/img.png" alt="Profile photo" />);
      expect(screen.getByAltText('Profile photo')).toBeInTheDocument();
    });

    it('uses default alt text when not provided', () => {
      render(<Avatar src="https://example.com/img.png" />);
      expect(screen.getByAltText('Avatar')).toBeInTheDocument();
    });
  });

  // ── Fallback (initials) ───────────────────────────────────────────────────

  describe('Fallback initials', () => {
    it('displays fallback text when no src is provided', () => {
      render(<Avatar fallback="AB" />);
      expect(screen.getByText('AB')).toBeInTheDocument();
    });

    it('renders children when provided (MUI-style initials)', () => {
      render(<Avatar>JD</Avatar>);
      expect(screen.getByText('JD')).toBeInTheDocument();
    });
  });

  // ── Size variants ─────────────────────────────────────────────────────────

  describe('Size variants', () => {
    const sizes = ['xs', 'sm', 'md', 'lg', 'xl', '2xl'] as const;

    sizes.forEach((size) => {
      it(`renders without error with size="${size}"`, () => {
        const { container } = render(<Avatar size={size} fallback="AB" />);
        expect(container.firstChild).toBeTruthy();
      });
    });

    it('accepts MUI-style size aliases', () => {
      render(<Avatar size="small" fallback="AB" />);
      render(<Avatar size="medium" fallback="AB" />);
      render(<Avatar size="large" fallback="AB" />);
      // All render without error
      expect(screen.getAllByText('AB')).toHaveLength(3);
    });
  });

  // ── Shape variants ────────────────────────────────────────────────────────

  describe('Shape variants', () => {
    it('renders circle shape', () => {
      const { container } = render(<Avatar shape="circle" fallback="AB" />);
      expect(container.firstChild).toHaveStyle({ borderRadius: expect.anything() });
    });

    it('renders square shape', () => {
      const { container } = render(<Avatar shape="square" fallback="AB" />);
      expect(container.firstChild).toBeTruthy();
    });
  });

  // ── Status indicator ──────────────────────────────────────────────────────

  describe('Status indicator', () => {
    const statuses = ['online', 'offline', 'away', 'busy'] as const;

    statuses.forEach((status) => {
      it(`renders a status dot for status="${status}"`, () => {
        const { container } = render(<Avatar fallback="AB" status={status} />);
        // Status dot is a child positioned element
        const dots = container.querySelectorAll('span > span, div > span');
        expect(dots.length).toBeGreaterThan(0);
      });
    });
  });

  // ── Click handling ────────────────────────────────────────────────────────

  describe('Click handling', () => {
    it('calls onClick when the avatar is clicked', () => {
      const onClick = vi.fn();
      const { container } = render(<Avatar fallback="AB" onClick={onClick} />);
      fireEvent.click(container.firstChild as HTMLElement);
      expect(onClick).toHaveBeenCalledTimes(1);
    });
  });

  // ── className forwarding ──────────────────────────────────────────────────

  describe('className and title', () => {
    it('forwards className to root element', () => {
      const { container } = render(<Avatar className="my-avatar" fallback="AB" />);
      expect(container.querySelector('.my-avatar')).toBeTruthy();
    });

    it('applies title attribute when provided', () => {
      const { container } = render(<Avatar title="User name" fallback="AB" />);
      expect(container.firstChild).toHaveAttribute('title', 'User name');
    });
  });
});
