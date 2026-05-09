import React from 'react';
import { describe, expect, it } from 'vitest';

import { getNavigationSectionsForShellRole } from '../../layouts/DefaultLayout';
import { getDiscoverableRouteSurfaces } from '../../lib/routing/RouteSurfaceRegistry';

const NAV_SURFACE_PATHS = new Set([
  '/',
  '/data',
  '/pipelines',
  '/query',
  '/insights',
  '/trust',
  '/events',
  '/alerts',
  '/plugins',
  '/connectors',
  '/operations',
]);

function getSectionPaths(role: 'primary-user' | 'operator' | 'admin'): string[] {
  const sections = getNavigationSectionsForShellRole(role);
  return sections.flatMap((section) => section.items.map((item) => item.to)).sort();
}

function getExpectedPathsFromRegistry(role: 'primary-user' | 'operator' | 'admin'): string[] {
  return getDiscoverableRouteSurfaces(role)
    .map((route) => route.path)
    .filter((path) => NAV_SURFACE_PATHS.has(path))
    .sort();
}

describe('DefaultLayout navigation progressive disclosure', () => {
  it('matches registry discoverability for primary-user shell', () => {
    const paths = getSectionPaths('primary-user');

    expect(paths).toEqual(getExpectedPathsFromRegistry('primary-user'));
    expect(paths).not.toContain('/operations');
  });

  it('matches registry discoverability for operator shell', () => {
    const paths = getSectionPaths('operator');

    expect(paths).toEqual(getExpectedPathsFromRegistry('operator'));
    expect(paths).toContain('/insights');
    expect(paths).toContain('/plugins');
    expect(paths).not.toContain('/operations');
  });

  it('matches registry discoverability for admin shell', () => {
    const paths = getSectionPaths('admin');

    expect(paths).toEqual(getExpectedPathsFromRegistry('admin'));
    expect(paths).toContain('/operations');
    expect(paths).not.toContain('/settings');
  });
});