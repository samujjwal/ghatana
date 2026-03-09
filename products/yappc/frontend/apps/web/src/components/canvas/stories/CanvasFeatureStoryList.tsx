import { Search as SearchIcon } from 'lucide-react';
import { FeatureStories } from '@ghatana/canvas';
import { Alert, Box, Chip, Stack, Typography } from '@ghatana/ui';
import { InputAdornment, Tab, Tabs, TextField } from '@ghatana/ui';
import { useCallback, useEffect, useMemo, useState } from 'react';

const defaultCanvasFeatureStoryCategories =
  FeatureStories.canvasFeatureStoryCategories;

/**
 *
 */
type CanvasFeatureStory = FeatureStories.CanvasFeatureStory;
/**
 *
 */
type CanvasFeatureStoryCategory = FeatureStories.CanvasFeatureStoryCategory;

import { CanvasFeatureStoryCard } from './CanvasFeatureStoryCard';

const ALL_TAB_VALUE = 'all';
const ALL_PROGRESS_VALUE = 'all-progress';

/**
 *
 */
export interface CanvasFeatureStoryListProps {
  categories?: readonly CanvasFeatureStoryCategory[];
  defaultCategoryId?: string;
  searchPlaceholder?: string;
  highlightStoryId?: string;
  onStorySelect?: (story: CanvasFeatureStory) => void;
  showCategoryLabelOnCards?: boolean;
  initialProgressFilter?: string;
  'data-testid'?: string;
}

const buildTabMetadata = (
  categories: readonly CanvasFeatureStoryCategory[]
) => {
  const total = categories.reduce(
    (acc, category) => acc + category.stories.length,
    0
  );
  return [
    {
      id: ALL_TAB_VALUE,
      label: `All (${total})`,
      count: total,
    },
    ...categories.map((category) => ({
      id: category.id,
      label: `${category.title} (${category.stories.length})`,
      count: category.stories.length,
    })),
  ];
};

const matchSearch = (story: CanvasFeatureStory, term: string) => {
  if (!term) {
    return true;
  }
  const normalized = term.toLowerCase();
  const fields = [
    story.title,
    story.narrative,
    story.blueprintReference ?? '',
    story.progress?.status ?? '',
    story.progress?.summary ?? '',
    ...story.acceptanceCriteria.map(
      (criterion) => `${criterion.title ?? ''} ${criterion.summary}`
    ),
    ...story.tests.map(
      (test) => `${test.type} ${test.summary} ${test.targets.join(' ')}`
    ),
  ];
  return fields.some((field) => field.toLowerCase().includes(normalized));
};

const slugifyStatus = (status: string) =>
  status.toLowerCase().replace(/[^a-z0-9]+/g, '-');

const aggregateStatusCounts = (stories: CanvasFeatureStory[]) => {
  const counts = new Map<string, { label: string; count: number }>();
  stories.forEach((story) => {
    const statusLabel = story.progress?.status?.trim();
    if (!statusLabel) {
      return;
    }
    const value = statusLabel.toLowerCase();
    const existing = counts.get(value);
    if (existing) {
      existing.count += 1;
    } else {
      counts.set(value, { label: statusLabel, count: 1 });
    }
  });
  return counts;
};

const getStatusChipColor = (status: string) => {
  const normalized = status.trim().toLowerCase();
  switch (normalized) {
    case 'done':
    case 'completed':
    case 'shipped':
      return 'success';
    case 'in progress':
    case 'progressing':
      return 'warning';
    case 'blocked':
      return 'error';
    case 'planned':
    case 'backlog':
    case 'not started':
      return 'default';
    case 'paused':
      return 'info';
    default:
      return 'default';
  }
};

/**
 *
 */
export function CanvasFeatureStoryList({
  categories = defaultCanvasFeatureStoryCategories,
  defaultCategoryId,
  searchPlaceholder = 'Search stories, acceptance criteria, or tests…',
  highlightStoryId,
  onStorySelect,
  showCategoryLabelOnCards = false,
  initialProgressFilter,
  'data-testid': dataTestId = 'canvas-feature-story-list',
}: CanvasFeatureStoryListProps) {
  const [activeCategoryId, setActiveCategoryId] = useState(
    defaultCategoryId ?? categories[0]?.id ?? ALL_TAB_VALUE
  );
  const [searchTerm, setSearchTerm] = useState('');
  const [progressFilter, setProgressFilter] = useState(() => {
    if (initialProgressFilter) {
      const normalized = initialProgressFilter.toLowerCase();
      return normalized === 'all' ? ALL_PROGRESS_VALUE : normalized;
    }
    if (typeof window === 'undefined') {
      return ALL_PROGRESS_VALUE;
    }
    try {
      const stored = window.localStorage.getItem(
        'canvas-feature-progress-filter'
      );
      if (!stored) {
        return ALL_PROGRESS_VALUE;
      }
      return stored;
    } catch (error) {
      return ALL_PROGRESS_VALUE;
    }
  });

  const updateProgressFilter = useCallback((next: string) => {
    setProgressFilter(next);
    if (typeof window !== 'undefined') {
      try {
        if (next === ALL_PROGRESS_VALUE) {
          window.localStorage.removeItem('canvas-feature-progress-filter');
        } else {
          window.localStorage.setItem('canvas-feature-progress-filter', next);
        }
      } catch (error) {
        // Ignore storage errors (e.g. private mode)
      }
    }
  }, []);

  useEffect(() => {
    if (!initialProgressFilter) {
      return;
    }
    const normalized = initialProgressFilter.toLowerCase();
    const nextValue = normalized === 'all' ? ALL_PROGRESS_VALUE : normalized;
    updateProgressFilter(nextValue);
  }, [initialProgressFilter, updateProgressFilter]);

  const tabs = useMemo(() => buildTabMetadata(categories), [categories]);

  const progressOptions = useMemo(() => {
    const statusMap = new Map<string, string>();
    categories.forEach((category) => {
      category.stories.forEach((story) => {
        const status = story.progress?.status?.trim();
        if (!status) return;
        const normalized = status.toLowerCase();
        if (!statusMap.has(normalized)) {
          statusMap.set(normalized, status);
        }
      });
    });
    return Array.from(statusMap.entries())
      .map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label));
  }, [categories]);

  const filteredStories = useMemo(() => {
    const normalizedSearch = searchTerm.trim();
    return categories.flatMap((category) => {
      if (
        activeCategoryId !== ALL_TAB_VALUE &&
        category.id !== activeCategoryId
      ) {
        return [];
      }

      return category.stories
        .filter((story) => {
          const matchesSearch = matchSearch(story, normalizedSearch);
          if (!matchesSearch) {
            return false;
          }
          if (progressFilter === ALL_PROGRESS_VALUE) {
            return true;
          }
          const storyStatus = story.progress?.status?.trim().toLowerCase();
          return storyStatus === progressFilter;
        })
        .map((story) => ({
          story,
          category,
        }));
    });
  }, [categories, activeCategoryId, searchTerm, progressFilter]);

  const totalStatusCounts = useMemo(
    () =>
      aggregateStatusCounts(categories.flatMap((category) => category.stories)),
    [categories]
  );

  const filteredStatusCounts = useMemo(
    () => aggregateStatusCounts(filteredStories.map(({ story }) => story)),
    [filteredStories]
  );

  const hasResults = filteredStories.length > 0;

  const activeTabValue =
    tabs.some((tab) => tab.id === activeCategoryId) ||
    activeCategoryId === ALL_TAB_VALUE
      ? activeCategoryId
      : (tabs[0]?.id ?? ALL_TAB_VALUE);

  return (
    <Box data-testid={dataTestId}>
      <Stack spacing={3}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={2}
          alignItems={{ md: 'center' }}
        >
          <Tabs
            value={activeTabValue}
            onChange={(_event, value) => setActiveCategoryId(value)}
            variant="scrollable"
            allowScrollButtonsMobile
            aria-label="Canvas feature story categories"
            data-testid="canvas-feature-story-tabs"
          >
            {tabs.map((tab) => (
              <Tab
                key={tab.id}
                value={tab.id}
                label={tab.label}
                id={`canvas-feature-tab-${tab.id}`}
              />
            ))}
          </Tabs>
          <TextField
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            placeholder={searchPlaceholder}
            aria-label="Search canvas feature stories"
            size="sm"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon size={16} aria-hidden />
                </InputAdornment>
              ),
            }}
            inputProps={{ 'data-testid': 'canvas-feature-story-search-input' }}
            className="min-w-full md:min-w-[320px]"
            data-testid="canvas-feature-story-search"
          />
          {progressOptions.length ? (
            <TextField
              select
              SelectProps={{ native: true }}
              label="Progress status"
              size="sm"
              value={progressFilter}
              onChange={(event) => {
                const next = event.target.value;
                updateProgressFilter(next);
              }}
              className="min-w-full md:min-w-[200px]"
              data-testid="canvas-feature-story-progress-filter"
              inputProps={{
                'data-testid': 'canvas-feature-story-progress-filter-input',
              }}
            >
              <option value={ALL_PROGRESS_VALUE}>All statuses</option>
              {progressOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </TextField>
          ) : null}
        </Stack>

        <Typography as="p" className="text-sm" color="text.secondary">
          Showing {filteredStories.length} of{' '}
          {tabs.find((tab) => tab.id === activeTabValue)?.count ??
            filteredStories.length}{' '}
          stories
          {searchTerm ? ` matching “${searchTerm.trim()}”` : ''}
          {progressFilter !== ALL_PROGRESS_VALUE
            ? ` with status ${progressOptions.find((option) => option.value === progressFilter)?.label ?? ''}`
            : ''}
          .
        </Typography>

        {progressOptions.length ? (
          <Stack spacing={1}>
            <Box>
              <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                Status summary (all stories)
              </Typography>
              <Stack
                direction="row"
                spacing={1}
                flexWrap="wrap"
                data-testid="canvas-feature-story-status-summary-all"
              >
                {progressOptions.map((option) => {
                  const slug = slugifyStatus(option.value);
                  const count = totalStatusCounts.get(option.value)?.count ?? 0;
                  return (
                    <Chip
                      key={`all-${option.value}`}
                      label={`${option.label}: ${count}`}
                      size="sm"
                      tone={
                        getStatusChipColor(option.label) === 'error'
                          ? 'danger'
                          : (getStatusChipColor(option.label) as unknown)
                      }
                      variant={count > 0 ? 'filled' : 'outlined'}
                      data-testid={`canvas-feature-story-status-summary-all-status-${slug}`}
                    />
                  );
                })}
              </Stack>
            </Box>

            <Box>
              <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                Status summary (visible list)
              </Typography>
              <Stack
                direction="row"
                spacing={1}
                flexWrap="wrap"
                data-testid="canvas-feature-story-status-summary-filtered"
              >
                {progressOptions.map((option) => {
                  const slug = slugifyStatus(option.value);
                  const count =
                    filteredStatusCounts.get(option.value)?.count ?? 0;
                  return (
                    <Chip
                      key={`filtered-${option.value}`}
                      label={`${option.label}: ${count}`}
                      size="sm"
                      tone={
                        getStatusChipColor(option.label) === 'error'
                          ? 'danger'
                          : (getStatusChipColor(option.label) as unknown)
                      }
                      variant={count > 0 ? 'filled' : 'outlined'}
                      data-testid={`canvas-feature-story-status-summary-filtered-status-${slug}`}
                    />
                  );
                })}
              </Stack>
            </Box>
          </Stack>
        ) : null}

        {hasResults ? (
          <Stack spacing={2}>
            {filteredStories.map(({ story, category }) => (
              <CanvasFeatureStoryCard
                key={story.id}
                story={story}
                highlight={story.id === highlightStoryId}
                onStorySelect={onStorySelect}
                showCategoryLabel={
                  showCategoryLabelOnCards || activeTabValue === ALL_TAB_VALUE
                }
                data-testid={`canvas-feature-story-card-${story.id}`}
                aria-label={`${story.title} (${category.title})`}
              />
            ))}
          </Stack>
        ) : (
          <Alert severity="info" variant="outlined">
            No stories match the current category and search filter. Try
            switching categories or clearing the search term.
          </Alert>
        )}
      </Stack>
    </Box>
  );
}
