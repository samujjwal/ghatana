/**
 * Lifecycle Artifact Types — Unit Tests
 *
 * Tests for the LIFECYCLE_ARTIFACT_CATALOG, helper functions,
 * and the LifecycleArtifactKind constant object.
 *
 * @doc.type test
 * @doc.purpose Verify lifecycle artifact type definitions and helpers
 * @doc.layer product
 * @doc.pattern Type Tests
 */

import { describe, it, expect } from 'vitest';

import {
  LifecycleArtifactKind,
  LIFECYCLE_ARTIFACT_CATALOG,
  getArtifactsForPhase,
  createArtifactTag,
} from '../lifecycle-artifacts';
import { LifecyclePhase, LIFECYCLE_PHASE } from '@/types/lifecycle';

describe('LifecycleArtifactKind', () => {
  it('exposes all 17 artifact kinds', () => {
    const kinds = Object.values(LifecycleArtifactKind);
    expect(kinds).toHaveLength(17);
  });

  it('allows enum-style access', () => {
    expect(LifecycleArtifactKind.IDEA_BRIEF).toBe('idea_brief');
    expect(LifecycleArtifactKind.INCIDENT_REPORT).toBe('incident_report');
    expect(LifecycleArtifactKind.LEARNING_RECORD).toBe('learning_record');
  });

  it('values are lowercase_snake_case strings', () => {
    Object.values(LifecycleArtifactKind).forEach((kind) => {
      expect(kind).toMatch(/^[a-z][a-z_]+$/);
    });
  });
});

describe('LIFECYCLE_ARTIFACT_CATALOG', () => {
  it('has an entry for every LifecycleArtifactKind value', () => {
    Object.values(LifecycleArtifactKind).forEach((kind) => {
      expect(LIFECYCLE_ARTIFACT_CATALOG[kind]).toBeDefined();
    });
  });

  it('every entry has required metadata fields', () => {
    Object.values(LIFECYCLE_ARTIFACT_CATALOG).forEach((meta) => {
      expect(meta.kind).toBeTruthy();
      expect(meta.label).toBeTruthy();
      expect(meta.description).toBeTruthy();
      expect(meta.icon).toBeTruthy();
      expect(meta.phase).toBeTruthy();
      expect(Array.isArray(meta.requiredUpstream)).toBe(true);
      expect(meta.placement).toBeDefined();
      expect(['app', 'canvas', 'preview', 'deploy']).toContain(
        meta.placement.surface
      );
      expect(['tab', 'panel', 'drawer', 'stage']).toContain(
        meta.placement.paramType
      );
      expect(meta.placement.param).toBeTruthy();
    });
  });

  it('every entry kind matches its catalog key', () => {
    (
      Object.entries(LIFECYCLE_ARTIFACT_CATALOG) as [string, { kind: string }][]
    ).forEach(([key, meta]) => {
      expect(meta.kind).toBe(key);
    });
  });

  describe('phase assignments', () => {
    it('maps INTENT phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.idea_brief.phase).toBe(
        LifecyclePhase.INTENT
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.research_pack.phase).toBe(
        LifecyclePhase.INTENT
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.problem_statement.phase).toBe(
        LifecyclePhase.INTENT
      );
    });

    it('maps SHAPE phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.requirements.phase).toBe(
        LifecyclePhase.SHAPE
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.adr.phase).toBe(LifecyclePhase.SHAPE);
      expect(LIFECYCLE_ARTIFACT_CATALOG.ux_spec.phase).toBe(
        LifecyclePhase.SHAPE
      );
    });

    it('maps VALIDATE phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.threat_model.phase).toBe(
        LifecyclePhase.VALIDATE
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.validation_report.phase).toBe(
        LifecyclePhase.VALIDATE
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.simulation_results.phase).toBe(
        LifecyclePhase.VALIDATE
      );
    });

    it('maps GENERATE phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.delivery_plan.phase).toBe(
        LifecyclePhase.GENERATE
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.release_strategy.phase).toBe(
        LifecyclePhase.GENERATE
      );
    });

    it('maps RUN phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.evidence_pack.phase).toBe(
        LifecyclePhase.RUN
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.release_packet.phase).toBe(
        LifecyclePhase.RUN
      );
    });

    it('maps OBSERVE phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.ops_baseline.phase).toBe(
        LifecyclePhase.OBSERVE
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.incident_report.phase).toBe(
        LifecyclePhase.OBSERVE
      );
    });

    it('maps IMPROVE phase artifacts correctly', () => {
      expect(LIFECYCLE_ARTIFACT_CATALOG.enhancement_requests.phase).toBe(
        LifecyclePhase.IMPROVE
      );
      expect(LIFECYCLE_ARTIFACT_CATALOG.learning_record.phase).toBe(
        LifecyclePhase.IMPROVE
      );
    });
  });

  describe('upstream dependencies', () => {
    it('idea_brief has no required upstream artifacts', () => {
      expect(
        LIFECYCLE_ARTIFACT_CATALOG.idea_brief.requiredUpstream
      ).toHaveLength(0);
    });

    it('research_pack requires idea_brief', () => {
      expect(
        LIFECYCLE_ARTIFACT_CATALOG.research_pack.requiredUpstream
      ).toContain('idea_brief');
    });

    it('problem_statement requires idea_brief and research_pack', () => {
      const upstream =
        LIFECYCLE_ARTIFACT_CATALOG.problem_statement.requiredUpstream;
      expect(upstream).toContain('idea_brief');
      expect(upstream).toContain('research_pack');
    });

    it('incident_report requires ops_baseline', () => {
      expect(
        LIFECYCLE_ARTIFACT_CATALOG.incident_report.requiredUpstream
      ).toContain('ops_baseline');
    });
  });

  describe('placements', () => {
    it('INTENT artifacts are placed on the app surface', () => {
      ['idea_brief', 'research_pack', 'problem_statement'].forEach((kind) => {
        expect(
          LIFECYCLE_ARTIFACT_CATALOG[
            kind as keyof typeof LIFECYCLE_ARTIFACT_CATALOG
          ].placement.surface
        ).toBe('app');
      });
    });

    it('OBSERVE and IMPROVE artifacts are placed on the deploy surface', () => {
      [
        'ops_baseline',
        'incident_report',
        'enhancement_requests',
        'learning_record',
      ].forEach((kind) => {
        expect(
          LIFECYCLE_ARTIFACT_CATALOG[
            kind as keyof typeof LIFECYCLE_ARTIFACT_CATALOG
          ].placement.surface
        ).toBe('deploy');
      });
    });
  });
});

describe('getArtifactsForPhase', () => {
  it('returns only INTENT phase artifacts for INTENT phase', () => {
    const kinds = getArtifactsForPhase(LifecyclePhase.INTENT);
    expect(kinds).toHaveLength(3);
    expect(kinds).toContain('idea_brief');
    expect(kinds).toContain('research_pack');
    expect(kinds).toContain('problem_statement');
  });

  it('returns only SHAPE phase artifacts for SHAPE phase', () => {
    const kinds = getArtifactsForPhase(LifecyclePhase.SHAPE);
    expect(kinds).toHaveLength(3);
    expect(kinds).toContain('requirements');
    expect(kinds).toContain('adr');
    expect(kinds).toContain('ux_spec');
  });

  it('returns only OBSERVE phase artifacts for OBSERVE phase', () => {
    const kinds = getArtifactsForPhase(LifecyclePhase.OBSERVE);
    expect(kinds).toContain('ops_baseline');
    expect(kinds).toContain('incident_report');
    expect(kinds).not.toContain('idea_brief');
  });

  it('covers all 17 artifact kinds across all phases', () => {
    const allKinds = LIFECYCLE_PHASE.flatMap((phase) => getArtifactsForPhase(phase));
    expect(allKinds).toHaveLength(17);
    expect(new Set(allKinds).size).toBe(17); // no duplicates
  });

  it('returns an empty array for a phase with no catalog entries (handles edge case)', () => {
    // Cast to LifecyclePhase to simulate an unknown phase
    const unknown = 'UNKNOWN' as LifecyclePhase;
    expect(getArtifactsForPhase(unknown)).toEqual([]);
  });
});

describe('createArtifactTag', () => {
  it('creates a properly prefixed tag for a known kind', () => {
    expect(createArtifactTag('idea_brief')).toBe('artifact:idea_brief');
    expect(createArtifactTag('incident_report')).toBe(
      'artifact:incident_report'
    );
  });

  it('creates tags for all catalog kinds without throws', () => {
    expect(() => {
      Object.values(LifecycleArtifactKind).forEach((kind) =>
        createArtifactTag(kind)
      );
    }).not.toThrow();
  });

  it('always produces a string starting with "artifact:"', () => {
    Object.values(LifecycleArtifactKind).forEach((kind) => {
      expect(createArtifactTag(kind)).toMatch(/^artifact:/);
    });
  });
});
