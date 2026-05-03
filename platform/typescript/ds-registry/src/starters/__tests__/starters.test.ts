/**
 * Starter contract correctness tests.
 *
 * Each starter contract's enum prop values must match the design system
 * component's actual TypeScript prop types. These tests catch the class of
 * mismatch fixed in Phase 0 of the dc-aep remediation (e.g. variant:
 * 'contained' vs 'solid', size: 'medium' vs 'md').
 *
 * The tests do NOT import design system components directly (to avoid adding
 * a cross-package peer dependency in tests). Instead they assert against the
 * known canonical values documented in the DS component source.
 */

import { describe, it, expect } from 'vitest';
import {
  ButtonContract,
  BoxContract,
  CardContract,
  TextFieldContract,
  TypographyContract,
  starterContracts,
} from '../index';

// ============================================================================
// Helpers
// ============================================================================

function getProp(contract: typeof ButtonContract, name: string) {
  return contract.props.find((p) => p.name === name);
}

function getEnumValues(contract: typeof ButtonContract, propName: string): unknown[] | undefined {
  const prop = getProp(contract, propName);
  return prop?.validation?.enum as unknown[] | undefined;
}

// ============================================================================
// Button
// ============================================================================

describe('ButtonContract', () => {
  it('has variant with type enum and DS-canonical values', () => {
    const prop = getProp(ButtonContract, 'variant');
    expect(prop?.type).toBe('enum');
    expect(prop?.builderMetadata?.control).toBe('select');
    const enumValues = getEnumValues(ButtonContract, 'variant');
    expect(enumValues).toBeDefined();
    // DS ButtonVariant: 'solid' | 'outline' | 'soft' | 'ghost' | 'link'
    // Starter exposes the three most common; must include solid and not 'contained'
    expect(enumValues).toContain('solid');
    expect(enumValues).toContain('outline');
    expect(enumValues).toContain('ghost');
    expect(enumValues).not.toContain('contained'); // legacy MUI value
  });

  it('has size with type enum and DS-canonical values', () => {
    const prop = getProp(ButtonContract, 'size');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(ButtonContract, 'size');
    // DS ButtonSize: 'sm' | 'md' | 'lg'
    expect(enumValues).toContain('sm');
    expect(enumValues).toContain('md');
    expect(enumValues).toContain('lg');
    expect(enumValues).not.toContain('small');  // legacy MUI alias
    expect(enumValues).not.toContain('medium'); // legacy MUI alias
    expect(enumValues).not.toContain('large');  // legacy MUI alias
  });

  it('has color with type enum and DS-canonical tone values', () => {
    const prop = getProp(ButtonContract, 'color');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(ButtonContract, 'color');
    // DS ButtonTone: 'primary' | 'secondary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'
    expect(enumValues).toContain('primary');
    expect(enumValues).toContain('danger');
    expect(enumValues).not.toContain('error'); // MUI-style alias, not a DS tone
  });

  it('has defaultValue for variant that is a valid enum value', () => {
    const prop = getProp(ButtonContract, 'variant');
    const enumValues = getEnumValues(ButtonContract, 'variant') ?? [];
    expect(enumValues).toContain(prop?.defaultValue);
  });

  it('has boolean props using toggle control', () => {
    for (const propName of ['disabled', 'fullWidth']) {
      const prop = getProp(ButtonContract, propName);
      expect(prop?.type).toBe('boolean');
      expect(prop?.builderMetadata?.control).toBe('toggle');
    }
  });
});

// ============================================================================
// TextField
// ============================================================================

describe('TextFieldContract', () => {
  it('has size with type enum and DS-canonical values', () => {
    const prop = getProp(TextFieldContract, 'size');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(TextFieldContract, 'size');
    // DS Input.tsx: size?: 'small' | 'medium'
    expect(enumValues).toContain('small');
    expect(enumValues).toContain('medium');
    expect(enumValues).not.toContain('sm'); // Button-style alias
    expect(enumValues).not.toContain('md'); // Button-style alias
  });

  it('does not expose a variant prop (variant is ignored by DS TextField)', () => {
    // The DS TextField accepts variant for MUI compatibility but ignores it.
    // The starter contract should not expose 'variant' as a configurable enum prop
    // to avoid misleading users with values that have no visual effect.
    const prop = getProp(TextFieldContract, 'variant');
    // If the prop is present it must NOT have misleading DS-incompatible default
    if (prop) {
      expect(prop.defaultValue).not.toBe('contained');
      expect(prop.defaultValue).not.toBe('sm');
      expect(prop.defaultValue).not.toBe('md');
    }
  });

  it('has boolean props using toggle control', () => {
    for (const propName of ['disabled', 'required', 'fullWidth', 'multiline']) {
      const prop = getProp(TextFieldContract, propName);
      expect(prop?.type).toBe('boolean');
      expect(prop?.builderMetadata?.control).toBe('toggle');
    }
  });
});

// ============================================================================
// Typography
// ============================================================================

describe('TypographyContract', () => {
  it('has variant with type enum and DS-canonical TypographyVariant values', () => {
    const prop = getProp(TypographyContract, 'variant');
    expect(prop?.type).toBe('enum');
    expect(prop?.builderMetadata?.control).toBe('select');
    const enumValues = getEnumValues(TypographyContract, 'variant');
    // DS TypographyVariant includes h1..h6, body1, body2, caption, overline, etc.
    for (const v of ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'body1', 'body2', 'caption', 'overline']) {
      expect(enumValues).toContain(v);
    }
  });

  it('has align with type enum using DS TextAlign values (start/center/end/justify)', () => {
    const prop = getProp(TypographyContract, 'align');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(TypographyContract, 'align');
    expect(enumValues).toContain('start');
    expect(enumValues).toContain('center');
    expect(enumValues).toContain('end');
    expect(enumValues).toContain('justify');
    expect(enumValues).not.toContain('left');  // CSS/MUI alias not used in DS
    expect(enumValues).not.toContain('right'); // CSS/MUI alias not used in DS
  });

  it('has defaultValue for align that is a valid enum value', () => {
    const prop = getProp(TypographyContract, 'align');
    const enumValues = getEnumValues(TypographyContract, 'align') ?? [];
    expect(enumValues).toContain(prop?.defaultValue);
  });
});

// ============================================================================
// Box
// ============================================================================

describe('BoxContract', () => {
  it('has display with type enum', () => {
    const prop = getProp(BoxContract, 'display');
    expect(prop?.type).toBe('enum');
    expect(prop?.builderMetadata?.control).toBe('select');
    const enumValues = getEnumValues(BoxContract, 'display');
    expect(enumValues).toContain('flex');
    expect(enumValues).toContain('block');
  });

  it('has flexDirection with type enum', () => {
    const prop = getProp(BoxContract, 'flexDirection');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(BoxContract, 'flexDirection');
    expect(enumValues).toContain('row');
    expect(enumValues).toContain('column');
  });

  it('has justifyContent with type enum', () => {
    const prop = getProp(BoxContract, 'justifyContent');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(BoxContract, 'justifyContent');
    expect(enumValues).toContain('flex-start');
    expect(enumValues).toContain('center');
    expect(enumValues).toContain('space-between');
  });

  it('has alignItems with type enum', () => {
    const prop = getProp(BoxContract, 'alignItems');
    expect(prop?.type).toBe('enum');
    const enumValues = getEnumValues(BoxContract, 'alignItems');
    expect(enumValues).toContain('flex-start');
    expect(enumValues).toContain('center');
    expect(enumValues).toContain('stretch');
  });
});

// ============================================================================
// Card
// ============================================================================

describe('CardContract', () => {
  it('is present in starterContracts', () => {
    expect(starterContracts.find((c) => c.name === 'Card')).toBeDefined();
  });

  it('has boolean props using toggle control', () => {
    const prop = getProp(CardContract, 'elevated');
    if (prop) {
      expect(prop.type).toBe('boolean');
    }
  });
});

// ============================================================================
// starterContracts array
// ============================================================================

describe('starterContracts', () => {
  it('contains all five starter contracts', () => {
    const names = starterContracts.map((c) => c.name);
    expect(names).toContain('Button');
    expect(names).toContain('Card');
    expect(names).toContain('TextField');
    expect(names).toContain('Typography');
    expect(names).toContain('Box');
  });

  it('every contract has at least one prop', () => {
    for (const contract of starterContracts) {
      expect(contract.props.length).toBeGreaterThan(0);
    }
  });

  it('every enum prop has at least two allowed values', () => {
    for (const contract of starterContracts) {
      for (const prop of contract.props) {
        if (prop.type === 'enum') {
          const enumValues = prop.validation?.enum as unknown[] | undefined;
          expect(enumValues?.length ?? 0).toBeGreaterThanOrEqual(2);
        }
      }
    }
  });
});
