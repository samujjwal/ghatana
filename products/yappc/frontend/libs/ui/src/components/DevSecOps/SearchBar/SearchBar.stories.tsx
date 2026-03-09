/**
 * SearchBar Component Stories
 *
 * @module DevSecOps/SearchBar/stories
 */

import { Box, Surface as Paper, Typography, InteractiveList as List, ListItem, ListItemText } from '@ghatana/ui';
import { useState } from 'react';

import { SearchBar } from './SearchBar';

import type { Meta, StoryObj } from '@storybook/react';


const meta: Meta<typeof SearchBar> = {
  title: 'DevSecOps/SearchBar',
  component: SearchBar,
  tags: ['autodocs'],
  argTypes: {
    debounceMs: {
      control: { type: 'number', min: 0, max: 1000, step: 100 },
    },
    loading: {
      control: 'boolean',
    },
    showRecent: {
      control: 'boolean',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof SearchBar>;

const mockRecentSearches = [
  'authentication',
  'payment gateway',
  'user profile',
  'api integration',
  'security audit',
];

const mockItems = [
  'Implement user authentication',
  'Fix payment bug',
  'Add search functionality',
  'Update API documentation',
  'Security vulnerability scan',
  'Performance optimization',
  'User interface improvements',
  'Database migration',
  'API rate limiting',
  'Authentication token refresh',
];

/**
 * Interactive wrapper for stories
 */
function InteractiveWrapper(
  props: Omit<React.ComponentProps<typeof SearchBar>, 'onChange' | 'resultsCount'>
) {
  const [value, setValue] = useState('');
  const [results, setResults] = useState(mockItems);

  const handleChange = (newValue: string) => {
    setValue(newValue);
    if (newValue) {
      const filtered = mockItems.filter((item) =>
        item.toLowerCase().includes(newValue.toLowerCase())
      );
      setResults(filtered);
    } else {
      setResults(mockItems);
    }
  };

  return (
    <Box className="p-6 max-w-[600px]">
      <SearchBar
        {...props}
        value={value}
        onChange={handleChange}
        resultsCount={value ? results.length : undefined}
      />

      <Paper className="mt-4 p-4">
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Results:
        </Typography>
        <List dense>
          {results.length > 0 ? (
            results.map((item, idx) => (
              <ListItem key={idx}>
                <ListItemText primary={item} />
              </ListItem>
            ))
          ) : (
            <Typography as="p" className="text-sm" color="text.secondary">
              No results found
            </Typography>
          )}
        </List>
      </Paper>
    </Box>
  );
}

/**
 * Default search bar
 */
export const Default: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    placeholder: 'Search items...',
  },
};

/**
 * With recent searches dropdown
 */
export const WithRecentSearches: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    placeholder: 'Search items...',
    showRecent: true,
    recentSearches: mockRecentSearches,
  },
};

/**
 * Loading state
 */
export const Loading: Story = {
  render: () => {
    const [value, setValue] = useState('authentication');

    return (
      <Box className="p-6 max-w-[600px]">
        <SearchBar
          value={value}
          onChange={setValue}
          placeholder="Search items..."
          loading
          resultsCount={5}
        />
        <Typography as="p" className="text-sm" color="text.secondary" className="mt-4">
          Searching for "{value}"...
        </Typography>
      </Box>
    );
  },
};

/**
 * With results count
 */
export const WithResultsCount: Story = {
  render: () => {
    const [value, setValue] = useState('auth');

    return (
      <Box className="p-6 max-w-[600px]">
        <SearchBar
          value={value}
          onChange={setValue}
          placeholder="Search items..."
          resultsCount={12}
        />
      </Box>
    );
  },
};

/**
 * Custom placeholder
 */
export const CustomPlaceholder: Story = {
  render: (args) => <InteractiveWrapper {...args} />,
  args: {
    placeholder: 'Search by title, description, or tags...',
  },
};

/**
 * Custom debounce time
 */
export const CustomDebounce: Story = {
  render: () => {
    const [value, setValue] = useState('');
    const [callCount, setCallCount] = useState(0);

    const handleChange = (newValue: string) => {
      setValue(newValue);
      setCallCount((prev) => prev + 1);
    };

    return (
      <Box className="p-6 max-w-[600px]">
        <Typography as="h6" gutterBottom>
          1000ms Debounce
        </Typography>
        <SearchBar
          value={value}
          onChange={handleChange}
          placeholder="Type to see debouncing..."
          debounceMs={1000}
        />
        <Paper className="p-4 mt-4 bg-gray-50 dark:bg-gray-800">
          <Typography as="p" className="text-sm">
            onChange called: <strong>{callCount}</strong> times
          </Typography>
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block">
            Try typing quickly. The onChange callback is only triggered 1 second after you stop
            typing.
          </Typography>
        </Paper>
      </Box>
    );
  },
};

/**
 * Recent searches interaction
 */
export const RecentSearchesInteraction: Story = {
  render: () => {
    const [value, setValue] = useState('');
    const [recentSearches, setRecentSearches] = useState(mockRecentSearches);
    const [selectedRecent, setSelectedRecent] = useState<string | null>(null);

    const handleRecentClick = (search: string) => {
      setSelectedRecent(search);
      // In a real app, you might add this to recent searches
    };

    return (
      <Box className="p-6 max-w-[600px]">
        <Typography as="h6" gutterBottom>
          Recent Searches Demo
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
          Click the search input to see recent searches
        </Typography>

        <SearchBar
          value={value}
          onChange={setValue}
          placeholder="Search items..."
          showRecent
          recentSearches={recentSearches}
          onRecentSearchClick={handleRecentClick}
        />

        {selectedRecent && (
          <Paper className="p-4 mt-4 bg-sky-50" >
            <Typography as="p" className="text-sm">
              Selected from recent: <strong>{selectedRecent}</strong>
            </Typography>
          </Paper>
        )}
      </Box>
    );
  },
};

/**
 * Full featured example
 */
export const FullFeatured: Story = {
  render: () => {
    const [value, setValue] = useState('');
    const [loading, setLoading] = useState(false);
    const [results, setResults] = useState(mockItems);
    const [recentSearches, setRecentSearches] = useState<string[]>([
      'authentication',
      'payment',
      'api',
    ]);

    const handleChange = (newValue: string) => {
      setValue(newValue);

      if (newValue) {
        setLoading(true);

        // Simulate API call
        setTimeout(() => {
          const filtered = mockItems.filter((item) =>
            item.toLowerCase().includes(newValue.toLowerCase())
          );
          setResults(filtered);
          setLoading(false);

          // Add to recent searches if not empty and not already there
          if (newValue && !recentSearches.includes(newValue)) {
            setRecentSearches([newValue, ...recentSearches].slice(0, 5));
          }
        }, 500);
      } else {
        setResults(mockItems);
        setLoading(false);
      }
    };

    const handleClear = () => {
      setResults(mockItems);
    };

    return (
      <Box className="p-6 max-w-[800px]">
        <Typography as="h5" gutterBottom>
          Full Featured Search
        </Typography>

        <SearchBar
          value={value}
          onChange={handleChange}
          onClear={handleClear}
          placeholder="Search items..."
          loading={loading}
          resultsCount={value ? results.length : undefined}
          showRecent
          recentSearches={recentSearches}
          debounceMs={300}
        />

        <Box className="mt-6 grid gap-4 grid-cols-2">
          <Paper className="p-4">
            <Typography as="p" className="text-sm font-medium" gutterBottom>
              Search Results ({results.length})
            </Typography>
            <List dense className="overflow-auto max-h-[300px]">
              {results.map((item, idx) => (
                <ListItem key={idx}>
                  <ListItemText primary={item} />
                </ListItem>
              ))}
            </List>
          </Paper>

          <Paper className="p-4 bg-gray-50 dark:bg-gray-800">
            <Typography as="p" className="text-sm font-medium" gutterBottom>
              Recent Searches
            </Typography>
            <List dense>
              {recentSearches.map((search, idx) => (
                <ListItem key={idx}>
                  <ListItemText primary={search} secondary={`Search ${idx + 1}`} />
                </ListItem>
              ))}
            </List>
          </Paper>
        </Box>
      </Box>
    );
  },
};

/**
 * Empty state
 */
export const Empty: Story = {
  render: () => (
    <Box className="p-6 max-w-[600px]">
      <SearchBar value="" onChange={() => {}} placeholder="No results..." resultsCount={0} />
    </Box>
  ),
};

/**
 * Comparison of debounce times
 */
export const DebounceComparison: Story = {
  render: () => {
    const [value1, setValue1] = useState('');
    const [value2, setValue2] = useState('');
    const [value3, setValue3] = useState('');
    const [callCount1, setCallCount1] = useState(0);
    const [callCount2, setCallCount2] = useState(0);
    const [callCount3, setCallCount3] = useState(0);

    return (
      <Box className="p-6">
        <Typography as="h5" gutterBottom>
          Debounce Time Comparison
        </Typography>

        <Box className="grid gap-6 mt-4">
          <Box>
            <Typography as="p" className="text-sm font-medium" gutterBottom>
              No Debounce (0ms)
            </Typography>
            <SearchBar
              value={value1}
              onChange={(v) => {
                setValue1(v);
                setCallCount1((prev) => prev + 1);
              }}
              debounceMs={0}
              placeholder="Immediate response..."
            />
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
              Calls: {callCount1}
            </Typography>
          </Box>

          <Box>
            <Typography as="p" className="text-sm font-medium" gutterBottom>
              Standard Debounce (300ms)
            </Typography>
            <SearchBar
              value={value2}
              onChange={(v) => {
                setValue2(v);
                setCallCount2((prev) => prev + 1);
              }}
              debounceMs={300}
              placeholder="300ms delay..."
            />
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
              Calls: {callCount2}
            </Typography>
          </Box>

          <Box>
            <Typography as="p" className="text-sm font-medium" gutterBottom>
              Slow Debounce (1000ms)
            </Typography>
            <SearchBar
              value={value3}
              onChange={(v) => {
                setValue3(v);
                setCallCount3((prev) => prev + 1);
              }}
              debounceMs={1000}
              placeholder="1000ms delay..."
            />
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
              Calls: {callCount3}
            </Typography>
          </Box>
        </Box>
      </Box>
    );
  },
};
