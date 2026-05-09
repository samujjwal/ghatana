import { Box, Chip, Stack, Typography } from '@ghatana/design-system';
import { Divider, InteractiveList as List, ListItem, ListItemText, Surface as Paper, type PaperProps } from '@ghatana/design-system';
import { forwardRef, memo, useCallback, useMemo } from 'react';
import { useI18n } from '../../../i18n/I18nProvider';

import type * as FeatureStories from './data';

/**
 *
 */
type CanvasFeatureStory = FeatureStories.CanvasFeatureStory;
type CanvasStoryTestReference = FeatureStories.CanvasStoryTestReference;

/**
 *
 */
export interface CanvasFeatureStoryCardProps extends Omit<
  PaperProps,
  'children'
> {
  story: CanvasFeatureStory;
  showCategoryLabel?: boolean;
  highlight?: boolean;
  onStorySelect?: (story: CanvasFeatureStory) => void;
  blueprintChipVariant?: 'outlined' | 'filled';
  'data-testid'?: string;
}

const testTypeColor: Record<
  string,
  | 'default'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'info'
  | 'danger'
> = {
  Unit: 'primary',
  Integration: 'secondary',
  E2E: 'success',
  CI: 'info',
  Perf: 'warning',
  Performance: 'warning',
  Ops: 'info',
  Security: 'danger',
  Accessibility: 'secondary',
};

const progressColorMap: Record<
  string,
  | 'default'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'info'
  | 'danger'
> = {
  done: 'success',
  completed: 'success',
  shipped: 'success',
  'in progress': 'warning',
  progressing: 'warning',
  planned: 'default',
  backlog: 'default',
  'not started': 'default',
  blocked: 'danger',
  paused: 'info',
};

const resolveProgressColor = (status: string | undefined) => {
  if (!status) {
    return 'default';
  }
  const normalized = status.trim().toLowerCase();
  return progressColorMap[normalized] ?? 'default';
};

const GENERATED_PROGRESS_SUMMARY =
  'Status not documented in docs/canvas-feature-stories.md yet.';

const renderTestTargets = (test: CanvasStoryTestReference) => {
  if (!test.targets.length) {
    return null;
  }

  return (
    <Stack
      direction="row"
      spacing={1}
      flexWrap="wrap"
      role="list"
      aria-label={t('canvas.featureStory.testTargets')}
      className="mt-1"
    >
      {test.targets.map((target) => (
        <Chip
          key={target}
          label={target}
          size="sm"
          variant="outlined"
          tone="default"
          style={{
            fontFamily: 'var(--mui-fontFamilyCode, "Roboto Mono", monospace)',
          }}
        />
      ))}
    </Stack>
  );
};

const CanvasFeatureStoryCardComponent = forwardRef<
  HTMLDivElement,
  CanvasFeatureStoryCardProps
>(
  (
    {
      story,
      showCategoryLabel = false,
      highlight = false,
      onStorySelect,
      blueprintChipVariant = 'outlined',
      sx,
      'data-testid': dataTestId,
      ...paperProps
    },
    ref
  ) => {
    const { t } = useI18n();
    const cardTitleId = useMemo(() => `${story.slug}-title`, [story.slug]);

    const interactive = typeof onStorySelect === 'function';
    const handleActivate = useCallback(() => {
      if (interactive) {
        onStorySelect?.(story);
      }
    }, [interactive, onStorySelect, story]);

    const handleKeyDown = useCallback(
      (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (!interactive) {
          return;
        }
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          handleActivate();
        }
      },
      [handleActivate, interactive]
    );

    return (
      <Paper
        ref={ref}
        variant="outlined"
        role="article"
        aria-labelledby={cardTitleId}
        tabIndex={interactive ? 0 : undefined}
        data-testid={dataTestId ?? `canvas-feature-story-${story.id}`}
        onClick={interactive ? handleActivate : undefined}
        onKeyDown={handleKeyDown}
        style={{
          padding: 24,
          borderWidth: highlight ? 2 : 1,
          borderColor: highlight ? '#1976d2' : 'rgba(0, 0, 0, 0.12)',
          borderRadius: 8,
          cursor: interactive ? 'pointer' : 'default',
          transition: 'border-color 200ms cubic-bezier(0.4, 0, 0.2, 1), box-shadow 200ms cubic-bezier(0.4, 0, 0.2, 1)',
          boxShadow: highlight ? '0px 2px 4px -1px rgba(0,0,0,0.2), 0px 4px 5px 0px rgba(0,0,0,0.14), 0px 1px 10px 0px rgba(0,0,0,0.12)' : 'none',
        }}
        className={`border border-solid ${interactive ? 'hover:border-info-border hover:shadow-md' : ''} focus-visible:outline-2 focus-visible:outline-blue-600 focus-visible:outline-offset-4`}
        {...paperProps}
      >
        <Stack spacing={2} divider={<Divider aria-hidden />}>
          <Stack
            direction="row"
            justifyContent="space-between"
            alignItems="flex-start"
            spacing={2}
          >
            <Box>
              {showCategoryLabel ? (
                <Typography
                  className="text-xs uppercase tracking-wider"
                  color="text.secondary"
                  data-testid={`canvas-feature-story-category-${story.categoryId}`}
                >
                  {story.categoryTitle}
                </Typography>
              ) : null}
              <Typography variant="h6" id={cardTitleId}>
                {story.title}
              </Typography>
              <Typography
                className="mt-1 text-sm"
                color="text.secondary"
              >
                {story.narrative}
              </Typography>
            </Box>
            <Stack spacing={1} alignItems="flex-end">
              <Chip
                label={`Story ${story.id}`}
                size="sm"
                tone="primary"
                variant="outlined"
              />
              {story.progress ? (
                <Chip
                  label={story.progress.status}
                  size="sm"
                  tone={resolveProgressColor(story.progress.status)}
                  variant="filled"
                  data-testid={`canvas-feature-story-progress-${story.id}`}
                />
              ) : null}
              {story.blueprintReference ? (
                <Chip
                  label={story.blueprintReference}
                  size="sm"
                  tone="default"
                  variant={blueprintChipVariant}
                  style={{ maxWidth: 200 }}
                />
              ) : null}
            </Stack>
          </Stack>

          {story.progress?.summary ? (
            <Box>
              <Typography className="text-sm font-medium" gutterBottom>
                Recent Updates
              </Typography>
              <Typography className="text-sm" color="text.secondary">
                <span
                  style={{
                    fontStyle:
                      story.progress.summary === GENERATED_PROGRESS_SUMMARY
                        ? 'italic'
                        : 'normal',
                    color:
                      story.progress.summary === GENERATED_PROGRESS_SUMMARY
                        ? 'var(--mui-palette-text-disabled)'
                        : undefined,
                  }}
                >
                  {story.progress.summary}
                </span>
              </Typography>
            </Box>
          ) : null}

          <Box>
            <Typography className="text-sm font-medium" gutterBottom>
              Acceptance Criteria
            </Typography>
            <List
              aria-label={`Acceptance criteria for ${story.title}`}
              data-testid={`canvas-feature-story-${story.id}-criteria`}
            >
              {story.acceptanceCriteria.map((criterion) => (
                <ListItem
                  key={criterion.id}
                  disablePadding
                  style={{ alignItems: 'flex-start' }}
                >
                  <ListItemText
                    primary={<Typography className="text-sm font-semibold">{criterion.title ?? criterion.summary}</Typography>}
                    secondary={criterion.title ? <Typography className="text-sm text-fg-muted">{criterion.summary}</Typography> : undefined}
                  />
                </ListItem>
              ))}
            </List>
          </Box>

          <Box>
            <Typography className="text-sm font-medium" gutterBottom>
              Tests
            </Typography>
            <List
              aria-label={`Tests validating ${story.title}`}
              data-testid={`canvas-feature-story-${story.id}-tests`}
            >
              {story.tests.map((test) => (
                <ListItem key={test.id} disablePadding style={{ alignItems: 'flex-start' }}>
                  <Stack spacing={0.5} className="w-full">
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Chip
                        label={test.type}
                        size="sm"
                        tone={testTypeColor[test.type] ?? 'default'}
                        variant="outlined"
                      />
                      <Typography className="text-sm">{test.summary}</Typography>
                    </Stack>
                    {renderTestTargets(test)}
                  </Stack>
                </ListItem>
              ))}
            </List>
          </Box>
        </Stack>
      </Paper>
    );
  }
);

CanvasFeatureStoryCardComponent.displayName = 'CanvasFeatureStoryCard';

export const CanvasFeatureStoryCard = memo(CanvasFeatureStoryCardComponent);
