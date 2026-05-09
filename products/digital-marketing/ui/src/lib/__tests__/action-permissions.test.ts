import { describe, expect, it } from 'vitest';
import { ACTION_MINIMUM_ROLES, canPerformAction, type DmosAction } from '../action-permissions';
import { dmosRouteManifest } from '@/routeManifest';

describe('action-permissions', () => {
  it('contains every route-manifest action in canonical action map', () => {
    const knownActions = new Set(Object.keys(ACTION_MINIMUM_ROLES));
    const routeActions = dmosRouteManifest.flatMap((route) => route.actions ?? []);

    for (const action of routeActions) {
      expect(knownActions.has(action)).toBe(true);
    }
  });

  it('enforces critical role matrix for mutation actions', () => {
    expect(canPerformAction(['viewer'], 'create-campaign')).toBe(false);
    expect(canPerformAction(['brand-manager'], 'create-campaign')).toBe(true);

    expect(canPerformAction(['brand-manager'], 'approve-strategy')).toBe(false);
    expect(canPerformAction(['marketing-director'], 'approve-strategy')).toBe(true);

    expect(canPerformAction(['marketing-director'], 'approve-budget')).toBe(false);
    expect(canPerformAction(['exec-sponsor'], 'approve-budget')).toBe(true);
    expect(canPerformAction(['admin'], 'approve-budget')).toBe(true);
  });

  it('defaults unknown role values to deny for privileged actions', () => {
    expect(canPerformAction(['unknown-role'], 'approve-budget')).toBe(false);
    expect(canPerformAction([], 'approve-budget')).toBe(false);
  });

  it('keeps all canonical actions typed to a valid minimum role', () => {
    const actions = Object.keys(ACTION_MINIMUM_ROLES) as DmosAction[];

    for (const action of actions) {
      expect(ACTION_MINIMUM_ROLES[action]).toBeTruthy();
    }
  });
});
