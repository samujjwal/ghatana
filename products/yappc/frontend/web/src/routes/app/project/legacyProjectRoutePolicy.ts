export type LegacyProjectRouteKey = 'canvas' | 'preview' | 'deploy' | 'lifecycle';
export type LegacyProjectCanonicalPhase = 'intent' | 'shape' | 'run' | 'observe';

export interface LegacyProjectRoutePolicy {
  readonly route: LegacyProjectRouteKey;
  readonly canonicalPhase: LegacyProjectCanonicalPhase;
  readonly redirectingKey: string;
}

export const LEGACY_PROJECT_ROUTE_POLICIES: Readonly<Record<LegacyProjectRouteKey, LegacyProjectRoutePolicy>> = {
  canvas: {
    route: 'canvas',
    canonicalPhase: 'shape',
    redirectingKey: 'phaseCockpit.legacy.canvas.redirecting',
  },
  preview: {
    route: 'preview',
    canonicalPhase: 'observe',
    redirectingKey: 'phaseCockpit.legacy.preview.redirecting',
  },
  deploy: {
    route: 'deploy',
    canonicalPhase: 'run',
    redirectingKey: 'phaseCockpit.legacy.deploy.redirecting',
  },
  lifecycle: {
    route: 'lifecycle',
    canonicalPhase: 'intent',
    redirectingKey: 'phaseCockpit.legacy.lifecycle.redirecting',
  },
} as const;
