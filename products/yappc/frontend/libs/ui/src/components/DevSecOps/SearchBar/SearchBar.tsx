/**
 * SearchBar Component
 *
 * A search input with debouncing, recent searches, and keyboard shortcuts.
 *
 * @module DevSecOps/SearchBar
 */

import { XCircle as ClearIcon } from 'lucide-react';
import { History as HistoryIcon } from 'lucide-react';
import { Search as SearchIcon } from 'lucide-react';
import { Box, Spinner as CircularProgress, IconButton, InputAdornment, InteractiveList as List, ListItem, ListItemButton, ListItemText, Surface as Paper, Popper, TextField, Typography } from '@ghatana/ui';
import { useEffect, useState } from 'react';

import type { SearchBarProps } from './types';

/**
 * SearchBar - Search input with debouncing and recent searches
 *
 * @param props - SearchBar component props
 * @returns Rendered SearchBar component
 *
 * @example
 * ```tsx
 * <SearchBar
 *   value={searchQuery}
 *   onChange={setSearchQuery}
 *   resultsCount={filteredItems.length}
 *   showRecent
 *   recentSearches={['authentication', 'payment', 'api']}
 * />
 * ```
 */
export function SearchBar({
  value = '',
  onChange,
  placeholder = 'Search items...',
  resultsCount,
  showRecent = true,
  recentSearches = [],
  onRecentSearchClick,
  onClear,
  debounceMs = 300,
  loading = false,
}: SearchBarProps) {
  const [inputValue, setInputValue] = useState(value);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [showRecentDropdown, setShowRecentDropdown] = useState(false);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      if (inputValue !== value) {
        onChange?.(inputValue);
      }
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [inputValue, debounceMs, onChange, value]);

  // Sync external value changes
  useEffect(() => {
    if (value !== inputValue) {
      setInputValue(value);
    }
  }, [value]);

  const handleClear = () => {
    setInputValue('');
    onChange?.('');
    onClear?.();
  };

  const handleFocus = (event: React.FocusEvent<HTMLInputElement>) => {
    if (showRecent && recentSearches.length > 0 && !inputValue) {
      setAnchorEl(event.currentTarget);
      setShowRecentDropdown(true);
    }
  };

  const handleBlur = () => {
    // Delay to allow click on recent search
    setTimeout(() => {
      setShowRecentDropdown(false);
    }, 200);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setInputValue(newValue);
    // Hide recent searches when user starts typing
    if (newValue && showRecentDropdown) {
      setShowRecentDropdown(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      handleClear();
    }
  };

  const handleRecentClick = (search: string) => {
    setInputValue(search);
    onChange?.(search);
    onRecentSearchClick?.(search);
    setShowRecentDropdown(false);
  };

  return (
    <Box className="relative w-full">
      <TextField
        fullWidth
        size="sm"
        value={inputValue}
        onChange={handleInputChange}
        onFocus={handleFocus}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon color="action" />
            </InputAdornment>
          ),
          endAdornment: (
            <InputAdornment position="end">
              {loading && <CircularProgress size={20} aria-label="Loading search results" />}
              {inputValue && !loading && (
                <IconButton 
                  size="sm" 
                  onClick={handleClear} 
                  edge="end"
                  aria-label="Clear search"
                >
                  <ClearIcon size={16} />
                </IconButton>
              )}
            </InputAdornment>
          ),
        }}
        className="bg-white dark:bg-gray-900"
      />

      {resultsCount !== undefined && inputValue && (
        <Typography
          as="span" className="text-xs text-gray-500"
          color="text.secondary"
          className="ml-2 mt-1 block"
        >
          {resultsCount} {resultsCount === 1 ? 'result' : 'results'}
        </Typography>
      )}

      {/* Recent Searches Dropdown */}
      <Popper
        open={showRecentDropdown}
        anchorEl={anchorEl}
        placement="bottom-start"
        className="z-[1300]"
        style={{ width: anchorEl?.offsetWidth || 'auto' }}
      >
        <Paper elevation={3} className="mt-1 overflow-auto max-h-[300px]">
          <List dense>
            <ListItem>
              <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                Recent Searches
              </Typography>
            </ListItem>
            {recentSearches.slice(0, 5).map((search, index) => (
              <ListItemButton key={index} onClick={() => handleRecentClick(search)}>
                <HistoryIcon size={16} className="mr-2 text-gray-500 dark:text-gray-400" />
                <ListItemText primary={search} />
              </ListItemButton>
            ))}
          </List>
        </Paper>
      </Popper>
    </Box>
  );
}
