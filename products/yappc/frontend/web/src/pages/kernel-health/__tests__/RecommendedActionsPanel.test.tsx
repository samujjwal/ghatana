/**
 * RecommendedActionsPanel Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for the RecommendedActionsPanel component
 * @doc.layer product
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { RecommendedActionsPanel, type ActionRecommendation } from '../RecommendedActionsPanel';

describe('RecommendedActionsPanel', () => {
  const productUnitId = 'digital-marketing';

  describe('empty state', () => {
    it('renders the empty state when there are no recommendations', () => {
      render(
        <RecommendedActionsPanel
          productUnitId={productUnitId}
          recommendations={[]}
        />
      );
      expect(screen.getByText(/no recommendations/i)).toBeInTheDocument();
    });
  });

  describe('with recommendations', () => {
    const criticalRec: ActionRecommendation = {
      severity: 'critical',
      title: 'Gate verification failed',
      description: 'The security gate failed during deployment phase.',
      actionType: 'fix-gate',
    };

    const warningRec: ActionRecommendation = {
      severity: 'warning',
      title: 'Deployment slow',
      description: 'Deployment took longer than expected threshold.',
      actionType: 'investigate',
    };

    const infoRec: ActionRecommendation = {
      severity: 'info',
      title: 'New version available',
      description: 'A newer lifecycle profile version is available.',
      actionType: 'upgrade',
    };

    it('renders a critical recommendation with its title and description', () => {
      render(
        <RecommendedActionsPanel
          productUnitId={productUnitId}
          recommendations={[criticalRec]}
        />
      );
      expect(screen.getByText('Gate verification failed')).toBeInTheDocument();
      expect(screen.getByText('The security gate failed during deployment phase.')).toBeInTheDocument();
    });

    it('renders severity badge for each recommendation', () => {
      render(
        <RecommendedActionsPanel
          productUnitId={productUnitId}
          recommendations={[criticalRec, warningRec, infoRec]}
        />
      );
      expect(screen.getByText('critical')).toBeInTheDocument();
      expect(screen.getByText('warning')).toBeInTheDocument();
      expect(screen.getByText('info')).toBeInTheDocument();
    });

    it('renders severity summary counts in the card header', () => {
      render(
        <RecommendedActionsPanel
          productUnitId={productUnitId}
          recommendations={[criticalRec, warningRec, infoRec]}
        />
      );
      expect(screen.getByText(/1 critical/i)).toBeInTheDocument();
      expect(screen.getByText(/1 warning/i)).toBeInTheDocument();
    });

    it('renders all provided recommendations', () => {
      render(
        <RecommendedActionsPanel
          productUnitId={productUnitId}
          recommendations={[criticalRec, warningRec, infoRec]}
        />
      );
      expect(screen.getByText('Gate verification failed')).toBeInTheDocument();
      expect(screen.getByText('Deployment slow')).toBeInTheDocument();
      expect(screen.getByText('New version available')).toBeInTheDocument();
    });
  });
});
