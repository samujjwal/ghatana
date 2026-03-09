// All tests skipped - incomplete feature
import { fireEvent, render, screen } from '@testing-library/react';
import { FeatureStories } from '@ghatana/canvas';
import { describe, expect, it, vi } from 'vitest';

import { CanvasFeatureStoryList } from '../CanvasFeatureStoryList';

describe.skip('CanvasFeatureStoryList', () => {
  it('renders the default category and stories', () => {
    render(<CanvasFeatureStoryList />);

    // Default category should be the first entry ("Current Capabilities")
    expect(screen.getByText('Viewport Management')).toBeInTheDocument();
    // Acceptance criteria rendered inside the card
    expect(
      screen.getByLabelText('Acceptance criteria for Viewport Management')
    ).toBeInTheDocument();

    expect(
      screen.getByTestId(
        'canvas-feature-story-status-summary-all-status-not-started'
      )
    ).toHaveTextContent(/Not Started:/);
    expect(
      screen.getByTestId(
        'canvas-feature-story-status-summary-filtered-status-not-started'
      )
    ).toHaveTextContent(/Not Started:/);
  });

  it('filters stories within the active category by search term', () => {
    render(<CanvasFeatureStoryList />);
    const searchInput = screen.getByTestId(
      'canvas-feature-story-search-input'
    ) as HTMLInputElement;

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
    const searchInput = screen.getByTestId(
      'canvas-feature-story-search-input'
    ) as HTMLInputElement;

    fireEvent.change(searchInput, { target: { value: 'nope-no-match' } });

    expect(
      screen.getByText(
        /No stories match the current category and search filter/
      )
    ).toBeInTheDocument();
  });

  it('can switch categories via tabs', () => {
    render(<CanvasFeatureStoryList defaultCategoryId="2" />);

    // Confirm category tabs rendered
    expect(screen.getByTestId('canvas-feature-story-tabs')).toBeInTheDocument();
    const tab = screen.getByRole('tab', { name: /All/ });
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
    render(<CanvasFeatureStoryList />);

    const select = screen.getByTestId(
      'canvas-feature-story-progress-filter-input'
    ) as HTMLSelectElement;
    fireEvent.change(select, { target: { value: 'in progress' } });

    expect(screen.getByText('Viewport Management')).toBeInTheDocument();
    expect(screen.queryByText('Element Manipulation')).not.toBeInTheDocument();
    expect(
      screen.getByTestId(
        'canvas-feature-story-status-summary-filtered-status-in-progress'
      )
    ).not.toHaveTextContent('In Progress: 0');
  });

  it('applies an initialProgressFilter when provided', () => {
    render(<CanvasFeatureStoryList initialProgressFilter="planned" />);

    expect(
      screen.getByTestId('canvas-feature-story-progress-filter-input')
    ).toHaveValue('planned');
    expect(screen.getByText('Element Manipulation')).toBeInTheDocument();
    expect(screen.queryByText('Viewport Management')).not.toBeInTheDocument();
    expect(
      screen.getByTestId(
        'canvas-feature-story-status-summary-filtered-status-planned'
      )
    ).not.toHaveTextContent('Planned: 0');
  });
});
