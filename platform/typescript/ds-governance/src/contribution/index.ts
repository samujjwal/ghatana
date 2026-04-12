/**
 * @fileoverview Contribution gates for design system components.
 */

import type { ComponentContract, ComponentProp } from '@ghatana/ds-schema';
import { z } from 'zod';

export interface ContributionGateResult {
  readonly approved: boolean;
  readonly blockers: readonly string[];
  readonly warnings: readonly string[];
}

/** Minimum requirements a component contract must meet to be accepted. */
export const ContributionRequirementsSchema = z.object({
  requireDescription: z.boolean().default(true),
  requireTests: z.boolean().default(true),
  requireA11yProps: z.boolean().default(true),
  minPropsDocumented: z.number().min(0).default(0),
}).strict();

export type ContributionRequirements = z.infer<typeof ContributionRequirementsSchema>;

const DEFAULT_REQUIREMENTS: ContributionRequirements = {
  requireDescription: true,
  requireTests: true,
  requireA11yProps: true,
  minPropsDocumented: 0,
};

/**
 * Runs contribution gates on a ComponentContract before it can be registered.
 */
export function runContributionGates(
  contract: ComponentContract,
  requirements: Partial<ContributionRequirements> = {},
): ContributionGateResult {
  const req = { ...DEFAULT_REQUIREMENTS, ...requirements };
  const blockers: string[] = [];
  const warnings: string[] = [];

  if (req.requireDescription && !contract.description) {
    blockers.push(`Component "${contract.name}" must have a description`);
  }

  if (req.requireA11yProps) {
    const hasAriaLabel = contract.props.some(
      (p: ComponentProp) => p.name === 'aria-label' || p.name === 'ariaLabel',
    );
    const hasRole = contract.props.some((p: ComponentProp) => p.name === 'role');
    if (!hasAriaLabel && !hasRole) {
      warnings.push(
        `Component "${contract.name}" has no aria-label or role prop — verify a11y coverage`,
      );
    }
  }

  if (req.minPropsDocumented > 0) {
    const documentedProps = contract.props.filter((p: ComponentProp) => Boolean(p.description));
    if (documentedProps.length < req.minPropsDocumented) {
      blockers.push(
        `Component "${contract.name}" requires at least ${req.minPropsDocumented} documented props, ` +
        `found ${documentedProps.length}`,
      );
    }
  }

  return {
    approved: blockers.length === 0,
    blockers,
    warnings,
  };
}
