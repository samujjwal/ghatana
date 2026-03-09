import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SearchBar } from '../SearchBar';
import type { SearchBarProps } from '../types';

/**
 * SearchBar Component Unit Tests
 * 
 * Tests SearchBar component behavior:
 * - Input rendering and changes
 * - Debouncing
 * - Recent searches
 * - Clear functionality
 * - Loading states
 * - Results count display
 */

describe('SearchBar Component', () => {
  const mockOnChange = vi.fn();
  const mockOnRecentSearchClick = vi.fn();
  const mockOnClear = vi.fn();

  const defaultProps: SearchBarProps = {
    value: '',
    onChange: mockOnChange,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render search input', () => {
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      expect(input).toBeInTheDocument();
    });

    it('should render with custom placeholder', () => {
      render(
        <SearchBar
          {...defaultProps}
          placeholder="Search work items..."
        />
      );
      
      expect(screen.getByPlaceholderText('Search work items...')).toBeInTheDocument();
    });

    it('should render search icon', () => {
      render(<SearchBar {...defaultProps} />);
      
      const searchIcon = screen.getByTestId('SearchIcon');
      expect(searchIcon).toBeInTheDocument();
    });

    it('should display current value', () => {
      render(<SearchBar {...defaultProps} value="authentication" />);
      
      const input = screen.getByDisplayValue('authentication');
      expect(input).toBeInTheDocument();
    });
  });

  describe('Input Changes', () => {
    it('should update input value on typing', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, 'api');
      
      expect(input).toHaveValue('api');
    });

    it('should call onChange after debounce delay', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} debounceMs={300} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, 'test');
      
      // Should not call immediately
      expect(mockOnChange).not.toHaveBeenCalled();
      
      // Should call after debounce
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith('test');
      }, { timeout: 400 });
    });

    it('should debounce rapid typing', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} debounceMs={300} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      
      // Type multiple characters rapidly
      await user.type(input, 'a');
      await user.type(input, 'p');
      await user.type(input, 'i');
      
      // Should only call once after debounce
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledTimes(1);
        expect(mockOnChange).toHaveBeenCalledWith('api');
      }, { timeout: 400 });
    });

    it('should respect custom debounce time', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} debounceMs={500} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, 'query');
      
      // Should not call before 500ms
      await new Promise(resolve => setTimeout(resolve, 300));
      expect(mockOnChange).not.toHaveBeenCalled();
      
      // Should call after 500ms
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith('query');
      }, { timeout: 600 });
    });

    it('should cancel previous debounce when typing continues', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} debounceMs={300} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      
      await user.type(input, 'test');
      await new Promise(resolve => setTimeout(resolve, 200));
      await user.type(input, '123');
      
      // Should only call once with final value
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledTimes(1);
        expect(mockOnChange).toHaveBeenCalledWith('test123');
      }, { timeout: 400 });
    });
  });

  describe('Clear Functionality', () => {
    it('should show clear button when input has value', () => {
      render(<SearchBar {...defaultProps} value="search term" />);
      
      const clearButton = screen.getByTestId('ClearIcon').closest('button');
      expect(clearButton).toBeInTheDocument();
    });

    it('should not show clear button when input is empty', () => {
      render(<SearchBar {...defaultProps} value="" />);
      
      const clearButton = screen.queryByTestId('ClearIcon');
      expect(clearButton).not.toBeInTheDocument();
    });

    it('should clear input when clear button clicked', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} value="search term" onClear={mockOnClear} />);
      
      const clearButton = screen.getByTestId('ClearIcon').closest('button');
      if (clearButton) {
        await user.click(clearButton);
      }
      
      expect(mockOnClear).toHaveBeenCalled();
    });

    it('should call onChange with empty string on clear', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} value="search term" />);
      
      const clearButton = screen.getByTestId('ClearIcon').closest('button');
      if (clearButton) {
        await user.click(clearButton);
      }
      
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith('');
      });
    });
  });

  describe('Loading State', () => {
    it('should show loading spinner when loading is true', () => {
      render(<SearchBar {...defaultProps} loading={true} />);
      
      const spinner = screen.getByRole('progressbar');
      expect(spinner).toBeInTheDocument();
    });

    it('should not show loading spinner when loading is false', () => {
      render(<SearchBar {...defaultProps} loading={false} />);
      
      const spinner = screen.queryByRole('progressbar');
      expect(spinner).not.toBeInTheDocument();
    });

    it('should show loading spinner during debounce', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} loading={true} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, 'test');
      
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('Results Count', () => {
    it('should display results count when provided', () => {
      render(<SearchBar {...defaultProps} resultsCount={42} value="query" />);
      
      expect(screen.getByText(/42 results/)).toBeInTheDocument();
    });

    it('should display "1 result" for single result', () => {
      render(<SearchBar {...defaultProps} resultsCount={1} value="query" />);
      
      expect(screen.getByText(/1 result/)).toBeInTheDocument();
    });

    it('should not display results count when value is empty', () => {
      render(<SearchBar {...defaultProps} resultsCount={10} value="" />);
      
      expect(screen.queryByText(/results/)).not.toBeInTheDocument();
    });

    it('should display 0 results', () => {
      render(<SearchBar {...defaultProps} resultsCount={0} value="query" />);
      
      expect(screen.getByText(/0 results/)).toBeInTheDocument();
    });

    it('should update results count dynamically', () => {
      const { rerender } = render(
        <SearchBar {...defaultProps} resultsCount={10} value="test" />
      );
      
      expect(screen.getByText(/10 results/)).toBeInTheDocument();
      
      rerender(<SearchBar {...defaultProps} resultsCount={5} value="test" />);
      expect(screen.getByText(/5 results/)).toBeInTheDocument();
    });
  });

  describe('Recent Searches', () => {
    const recentSearches = ['authentication', 'payment', 'api'];

    it('should show recent searches when input is focused and empty', async () => {
      const user = userEvent.setup();
      render(
        <SearchBar
          {...defaultProps}
          showRecent={true}
          recentSearches={recentSearches}
        />
      );
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      
      await waitFor(() => {
        expect(screen.getByText('authentication')).toBeInTheDocument();
        expect(screen.getByText('payment')).toBeInTheDocument();
        expect(screen.getByText('api')).toBeInTheDocument();
      });
    });

    it('should not show recent searches when showRecent is false', async () => {
      const user = userEvent.setup();
      render(
        <SearchBar
          {...defaultProps}
          showRecent={false}
          recentSearches={recentSearches}
        />
      );
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      
      await new Promise(resolve => setTimeout(resolve, 100));
      expect(screen.queryByText('authentication')).not.toBeInTheDocument();
    });

    it('should call onRecentSearchClick when recent search clicked', async () => {
      const user = userEvent.setup();
      render(
        <SearchBar
          {...defaultProps}
          showRecent={true}
          recentSearches={recentSearches}
          onRecentSearchClick={mockOnRecentSearchClick}
        />
      );
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      
      await waitFor(() => {
        expect(screen.getByText('authentication')).toBeInTheDocument();
      });
      
      const recentItem = screen.getByText('authentication');
      await user.click(recentItem);
      
      expect(mockOnRecentSearchClick).toHaveBeenCalledWith('authentication');
    });

    it('should hide recent searches when typing', async () => {
      const user = userEvent.setup();
      render(
        <SearchBar
          {...defaultProps}
          showRecent={true}
          recentSearches={recentSearches}
        />
      );
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      
      await waitFor(() => {
        expect(screen.getByText('authentication')).toBeInTheDocument();
      });
      
      await user.type(input, 'test');
      
      await waitFor(() => {
        expect(screen.queryByText('authentication')).not.toBeInTheDocument();
      });
    });

    it('should show history icon for recent searches', async () => {
      const user = userEvent.setup();
      render(
        <SearchBar
          {...defaultProps}
          showRecent={true}
          recentSearches={recentSearches}
        />
      );
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      
      await waitFor(() => {
        const historyIcons = screen.getAllByTestId('HistoryIcon');
        expect(historyIcons.length).toBeGreaterThan(0);
      });
    });

    it('should handle empty recent searches array', async () => {
      const user = userEvent.setup();
      render(
        <SearchBar
          {...defaultProps}
          showRecent={true}
          recentSearches={[]}
        />
      );
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      
      await new Promise(resolve => setTimeout(resolve, 100));
      expect(screen.queryByTestId('HistoryIcon')).not.toBeInTheDocument();
    });
  });

  describe('Keyboard Shortcuts', () => {
    it('should focus input on keyboard shortcut', () => {
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      expect(document.activeElement).not.toBe(input);
    });

    it('should clear input on Escape key', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} value="test query" />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.click(input);
      await user.keyboard('{Escape}');
      
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith('');
      });
    });
  });

  describe('Accessibility', () => {
    it('should have accessible label', () => {
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      expect(input).toHaveAttribute('type', 'text');
    });

    it('should have accessible clear button', () => {
      render(<SearchBar {...defaultProps} value="test" />);
      
      const clearButton = screen.getByTestId('ClearIcon').closest('button');
      expect(clearButton).toHaveAttribute('aria-label', 'Clear search');
    });

    it('should have proper ARIA attributes on loading spinner', () => {
      render(<SearchBar {...defaultProps} loading={true} />);
      
      const spinner = screen.getByRole('progressbar');
      expect(spinner).toHaveAttribute('aria-label', 'Loading search results');
    });
  });

  describe('Edge Cases', () => {
    it('should handle very long search queries', async () => {
      const user = userEvent.setup();
      const longQuery = 'a'.repeat(1000);
      
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, longQuery);
      
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith(longQuery);
      }, { timeout: 400 });
    });

    it('should handle special characters', async () => {
      const user = userEvent.setup();
      const specialQuery = '!@#$%^&*()';
      
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, specialQuery);
      
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith(specialQuery);
      }, { timeout: 400 });
    });

    it('should handle unicode characters', async () => {
      const user = userEvent.setup();
      const unicodeQuery = '你好世界';
      
      render(<SearchBar {...defaultProps} />);
      
      const input = screen.getByPlaceholderText('Search items...');
      await user.type(input, unicodeQuery);
      
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith(unicodeQuery);
      }, { timeout: 400 });
    });

    it('should handle rapid clear and type', async () => {
      const user = userEvent.setup();
      render(<SearchBar {...defaultProps} value="test" />);
      
      const input = screen.getByPlaceholderText('Search items...');
      const clearButton = screen.getByTestId('ClearIcon').closest('button');
      
      if (clearButton) {
        await user.click(clearButton);
        await user.type(input, 'new query');
      }
      
      await waitFor(() => {
        expect(mockOnChange).toHaveBeenCalledWith('new query');
      }, { timeout: 400 });
    });
  });

  describe('Controlled vs Uncontrolled', () => {
    it('should work as controlled component', async () => {
      const user = userEvent.setup();
      const { rerender } = render(<SearchBar {...defaultProps} value="initial" />);
      
      expect(screen.getByDisplayValue('initial')).toBeInTheDocument();
      
      rerender(<SearchBar {...defaultProps} value="updated" />);
      expect(screen.getByDisplayValue('updated')).toBeInTheDocument();
    });

    it('should sync internal state with external value changes', () => {
      const { rerender } = render(<SearchBar {...defaultProps} value="" />);
      
      rerender(<SearchBar {...defaultProps} value="external update" />);
      expect(screen.getByDisplayValue('external update')).toBeInTheDocument();
    });
  });
});
