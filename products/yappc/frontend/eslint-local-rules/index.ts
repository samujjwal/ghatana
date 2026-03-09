/**
 * ESLint plugin for YAPPC design system enforcement
 * 
 * This plugin provides custom rules to ensure consistent usage of the design system:
 * 
 * **Active Rules (Tailwind Migration):**
 * - no-hardcoded-colors: Prevent hex colors, require palette tokens
 * - prefer-yappc-ui: Require @ghatana/yappc-ui components instead of custom implementations
 * - prefer-tailwind-over-inline: Prefer className over style={{}} prop
 * - require-cn-utility: Require cn() for className merging
 * - no-arbitrary-tailwind: Prevent arbitrary values like p-[16px]
 * 
 * **Legacy Rules (MUI Migration - being phased out):**
 * - no-magic-spacing: Prevent pixel values, require spacing tokens
 * - prefer-sx-over-style: Prefer sx prop over style prop on MUI components
 */

import type { ESLint } from 'eslint';
import noHardcodedColors from './rules/no-hardcoded-colors.js';
import noMagicSpacing from './rules/no-magic-spacing.js';
import preferSxOverStyle from './rules/prefer-sx-over-style.js';
import preferYappcUI from './rules/prefer-yappc-ui.js';
import preferTailwindOverInline from './rules/prefer-tailwind-over-inline.js';
import requireCnUtility from './rules/require-cn-utility.js';
import noArbitraryTailwind from './rules/no-arbitrary-tailwind.js';

const plugin: ESLint.Plugin = {
  rules: {
    // Active rules
    'no-hardcoded-colors': noHardcodedColors,
    'prefer-yappc-ui': preferYappcUI,
    'prefer-tailwind-over-inline': preferTailwindOverInline,
    'require-cn-utility': requireCnUtility,
    'no-arbitrary-tailwind': noArbitraryTailwind,
    
    // Legacy rules (MUI migration)
    'no-magic-spacing': noMagicSpacing,
    'prefer-sx-over-style': preferSxOverStyle,
  },
  configs: {
    recommended: {
      rules: {
        // Active rules (enforced)
        'yappc-design-system/no-hardcoded-colors': 'warn',
        'yappc-design-system/prefer-yappc-ui': 'warn',
        'yappc-design-system/prefer-tailwind-over-inline': 'warn',
        'yappc-design-system/require-cn-utility': 'warn',
        'yappc-design-system/no-arbitrary-tailwind': 'warn',
        
        // Legacy rules (will be removed after Tailwind migration)
        'yappc-design-system/no-magic-spacing': 'off',
        'yappc-design-system/prefer-sx-over-style': 'off',
      },
    },
  },
};

export default plugin;

