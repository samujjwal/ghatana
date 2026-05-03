import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import * as FeatureStories from '../data';

import { CanvasFeatureStoryList } from '../CanvasFeatureStoryList';

describe('CanvasFeatureStoryList', () => {
  beforeEach(() => {
    window.localStorage.removeItem('canvas-feature-progress-filter');
  });

  it('renders the default category and stories', () => {
    render(<CanvasFeatureStoryList />);

    // Default category should be the first entry ("Current Capabilities")
    expect(screen.getByText('Viewport Management')).toBeInTheDocument();
    expect(screen.getByText('Smooth zooming')).toBeInTheDocument();

    expect(
      screen.getByTestId('canvas-feature-story-status-summary-all')
    ).toHaveTextContent(':');
    expect(
      screen.getByTestId('canvas-feature-story-status-summary-filtered')
    ).toHaveTextContent(':');
  });

  it('filters stories within the active category by search term', () => {
    render(<CanvasFeatureStoryList />);
    const searchInput = screen.getByRole('textbox', {
      name: 'Search canvas feature stories',
    }) as HTMLInputElement;

    fireEvent.change(searchInput, { target: { value: 'Keyboard Navigation' } });

    expect(screen.getByText('Keyboard Navigation')).toBeInTheDocument();
    expect(screen.queryByText('Viewport Management')).not.toBeInTheDocument();
  });

  it('invokes onStorySelect when a card is activated', () => {
    const handleSelect = vi.fn();
    render(
      <CanvasFeatureStoryList
        onStorySelect={handleSelect}
        highlightStoryId="1.1"
      />
    );

    const card = screen.getByTestId('canvas-feature-story-card-1.1');
    fireEvent.click(card);

    expect(handleSelect).toHaveBeenCalledTimes(1);
    const expectedStory = FeatureStories.canvasFeatureStoryById('1.1');
    expect(expectedStory).toBeTruthy();
    expect(handleSelect.mock.calls[0][0]).toBe(expectedStory);
  });

  it('shows an informative state when filters yield no results', () => {
    render(<CanvasFeatureStoryList />);
    const searchInput = screen.getByRole('textbox', {
      name: 'Search canvas feature stories',
    }) as HTMLInputElement;

    fireEvent.change(searchInput, { target: { value: 'nope-no-match' } });

    expect(
      screen.getByText(
        /No stories match the current category and search filter/
      )
    ).toBeInTheDocument();
  });

  it('can switch categories via tabs', () => {
    render(<CanvasFeatureStoryList defaultCategoryId="2" />);

    const tab = screen.getByRole('tab', { name: /^All \(/ });
    fireEvent.click(tab);

    // Story from another category should become visible in "All" tab
    const targetStory = FeatureStories.canvasFeatureStoryCategories
      .find((category) => category.id === '5')
      ?.stories.find((story) => story.id === '5.4');

    if (targetStory) {
      expect(screen.getByText(targetStory.title)).toBeInTheDocument();
    }
  });

  it('filters stories by progress status', () => {
    const category = FeatureStories.canvasFeatureStoryCategories.find(
      (entry) => entry.id === '1'
    );
    const candidateStories = category?.stories.filter(
      (story) => story.progress?.status
    );

    expect(candidateStories && candidateStories.length >= 2).toBeTruthy();

    const primaryStory = candidateStories?.[0];
    const secondaryStory = candidateStories?.find(
      (story) =>
        story.progress?.status?.toLowerCase() !==
        primaryStory?.progress?.status?.toLowerCase()
    );

    expect(primaryStory).toBeTruthy();
    expect(secondaryStory).toBeTruthy();

    render(<CanvasFeatureStoryList defaultCategoryId="1" />);

    const select = screen.getByTestId(
      'canvas-feature-story-progress-filter-input'
    ) as HTMLSelectElement;
    fireEvent.change(select, {
      target: { value: primaryStory?.progress?.status?.toLowerCase() },
    });

    expect(
      screen.getByTestId(`canvas-feature-story-card-${primaryStory?.id}`)
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId(`canvas-feature-story-card-${secondaryStory?.id}`)
    ).not.toBeInTheDocument();
    expect(
      screen.getByTestId(
        `canvas-feature-story-status-summary-filtered-status-${primaryStory?.progress?.status
          ?.toLowerCase()
          .replace(/[^a-z0-9]+/g, '-')}`
      )
    ).not.toHaveTextContent('In Progress: 0');
  });

  it('applies an initialProgressFilter when provided', () => {
    const category = FeatureStories.canvasFeatureStoryCategories.find(
      (entry) => entry.id === '1'
    );
    const story = category?.stories.find((entry) => entry.progress?.status);

    expect(story).toBeTruthy();

    const selectedStatus = story?.progress?.status?.toLowerCase() ?? '';
    render(<CanvasFeatureStoryList initialProgressFilter={selectedStatus} />);

    expect(
      screen.getByTestId('canvas-feature-story-progress-filter-input')
    ).toHaveValue(selectedStatus);
    expect(
      screen.getByTestId(`canvas-feature-story-card-${story?.id}`)
    ).toBeInTheDocument();
    expect(
      screen.getByTestId(
        `canvas-feature-story-status-summary-filtered-status-${selectedStatus.replace(
          /[^a-z0-9]+/g,
          '-'
        )}`
      )
    ).not.toHaveTextContent(': 0');
  });
});
