import React from 'react';
import { MemoryRouter } from 'react-router';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('../DeliveryPlanEditor', () => ({
  DeliveryPlanEditor: () => <div>Delivery Plan Editor</div>,
}));

vi.mock('../ReleaseStrategyConfigurator', () => ({
  ReleaseStrategyConfigurator: () => <div>Release Strategy Configurator</div>,
}));

vi.mock('../BuildProgressTracker', () => ({
  BuildProgressTracker: () => <div>Build Progress Tracker</div>,
}));

vi.mock('../ReleasePacketPanel', () => ({
  ReleasePacketPanel: () => <div>Release Packet Panel</div>,
}));

vi.mock('../DeploymentPanel', () => ({
  DeploymentPanel: ({ plan }: { plan: { strategy: string } }) => <div>{plan.strategy} deployment panel</div>,
}));

vi.mock('../CapacityDashboard', () => ({
  CapacityDashboard: ({ recommendation }: { recommendation: { action: string } }) => <div>{recommendation.action} capacity panel</div>,
}));

vi.mock('../../observe/HealthPanel', () => ({
  HealthPanel: () => <div>Health Panel</div>,
}));

vi.mock('../../observe/IncidentsPanel', () => ({
  IncidentsPanel: () => <div>Incidents Panel</div>,
}));

import { DeployPanelHost } from '../DeployPanelHost';

describe('DeployPanelHost', () => {
  it('renders the strategy panel when selected in the deployments segment', async () => {
    render(
      <MemoryRouter initialEntries={['/?segment=deployments&panel=strategy']}>
        <DeployPanelHost
          projectId="project-1"
          dataContext={{
            deploymentPlan: {
              strategy: 'CANARY',
            },
          }}
        />
      </MemoryRouter>
    );

    expect(await screen.findByText('CANARY deployment panel')).toBeInTheDocument();
  });

  it('renders the capacity panel when selected in the health segment', async () => {
    render(
      <MemoryRouter initialEntries={['/?segment=health&panel=capacity']}>
        <DeployPanelHost
          projectId="project-1"
          dataContext={{
            capacityRecommendation: {
              action: 'HOLD',
            },
          }}
        />
      </MemoryRouter>
    );

    expect(await screen.findByText('HOLD capacity panel')).toBeInTheDocument();
  });

  it('shows new strategy and capacity choices in the segment overviews', () => {
    const { rerender } = render(
      <MemoryRouter initialEntries={['/?segment=deployments']}>
        <DeployPanelHost projectId="project-1" dataContext={{}} />
      </MemoryRouter>
    );

    expect(screen.getByText('AI Strategy')).toBeInTheDocument();

    rerender(
      <MemoryRouter initialEntries={['/?segment=health']}>
        <DeployPanelHost projectId="project-1" dataContext={{}} />
      </MemoryRouter>
    );

    fireEvent.click(screen.getByRole('button', { name: /health/i }));
    expect(screen.getByText('Review scaling recommendation and monthly cost outlook')).toBeInTheDocument();
  });
});