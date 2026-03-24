import { useTheme } from '../../theme/ThemeContext';

import type { CSSProperties } from 'react';

/**
 *
 */
interface ThemeToggleProps {
  className?: string;
  style?: CSSProperties;
  size?: 'small' | 'medium' | 'large';
}

/**
 * Theme toggle button component
 * Switches between light and dark themes
 * 
 * @param className - Additional CSS classes
 * @param style - Inline styles
 * @param size - Button size variant
 * 
 * @example
 * ```tsx
 * <ThemeToggle size="medium" />
 * ```
 */
export function ThemeToggle({ 
  className = '', 
  style,
  size = 'medium' 
}: ThemeToggleProps) {
  const { mode, toggleTheme } = useTheme();

  const sizeMap = {
    small: '32px',
    medium: '40px',
    large: '48px',
  };

  const iconSize = {
    small: '16px',
    medium: '20px',
    large: '24px',
  };

  const buttonStyle: CSSProperties = {
    width: sizeMap[size],
    height: sizeMap[size],
    borderRadius: '50%',
    border: '1px solid',
    borderColor: mode === 'dark' ? '#424242' : '#e0e0e0',
    background: mode === 'dark' ? '#212121' : '#ffffff',
    color: mode === 'dark' ? '#fafafa' : '#212121',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'all 0.2s ease-in-out',
    ...style,
  };

  return (
    <button
      onClick={toggleTheme}
      className={className}
      style={buttonStyle}
      aria-label={`Switch to ${mode === 'light' ? 'dark' : 'light'} mode`}
      title={`Switch to ${mode === 'light' ? 'dark' : 'light'} mode`}
    >
      {mode === 'light' ? (
        <svg
          width={iconSize[size]}
          height={iconSize[size]}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      ) : (
        <svg
          width={iconSize[size]}
          height={iconSize[size]}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="12" cy="12" r="5" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
      )}
    </button>
  );
}
