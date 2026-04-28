/**
 * ESLint configuration for the TutorPutor mobile app.
 *
 * Aligns with the web and admin eslint.config.js guardrails:
 * - Blocks deprecated TutorPutor-local primitive imports
 * - Enforces TypeScript strict rules
 * - Enforces React Native accessibility patterns
 *
 * All three surfaces (web, admin, mobile) must satisfy the same
 * design-system and accessibility guardrail set.
 */

import tsParser from "@typescript-eslint/parser";
import tsPlugin from "@typescript-eslint/eslint-plugin";

export default [
  {
    ignores: [
      "node_modules/**",
      "android/**",
      "ios/**",
      "e2e/**",
      ".metro-cache/**",
    ],
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        sourceType: "module",
        ecmaVersion: "latest",
        ecmaFeatures: {
          jsx: true,
        },
      },
      globals: {
        Buffer: "readonly",
        URL: "readonly",
        __DEV__: "readonly",
        clearInterval: "readonly",
        clearTimeout: "readonly",
        console: "readonly",
        fetch: "readonly",
        process: "readonly",
        setInterval: "readonly",
        setTimeout: "readonly",
        require: "readonly",
        module: "readonly",
        exports: "readonly",
        global: "readonly",
      },
    },
    plugins: {
      "@typescript-eslint": tsPlugin,
    },
    rules: {
      // -----------------------------------------------------------------------
      // TypeScript strict checks (aligned with web/admin)
      // -----------------------------------------------------------------------
      "@typescript-eslint/consistent-type-imports": "error",
      "@typescript-eslint/no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          varsIgnorePattern: "^_",
        },
      ],
      "@typescript-eslint/no-explicit-any": "error",

      // -----------------------------------------------------------------------
      // Design-system guardrails
      //
      // Block deprecated TutorPutor-local UI primitive imports.
      // The canonical source is @ghatana/design-system; mobile surfaces
      // should use shared components where native equivalents exist.
      // -----------------------------------------------------------------------
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@tutorputor/ui/components/primitives*"],
              message:
                "Use shared UI components from @ghatana/design-system (or React Native core elements) instead of TutorPutor-local primitives.",
            },
            {
              // Forbid reaching into internal RN module paths — use public API
              group: ["react-native/Libraries/*"],
              message:
                "Import from 'react-native' directly, not from internal React Native library paths.",
            },
          ],
        },
      ],

      // -----------------------------------------------------------------------
      // React Native accessibility patterns
      //
      // Enforced as warnings so CI can surface them without blocking builds
      // during the initial migration window; upgrade to "error" once all
      // existing violations are fixed.
      // -----------------------------------------------------------------------

      // Interactive elements must carry an accessible label or hint
      // (expressed as a JSX prop rule via no-restricted-syntax)
      "no-restricted-syntax": [
        "warn",
        {
          // TouchableOpacity / TouchableHighlight / Pressable without
          // accessibilityLabel or accessible={false} explicit opt-out
          selector:
            "JSXOpeningElement[name.name=/^(TouchableOpacity|TouchableHighlight|Pressable)$/]:not(:has(JSXAttribute[name.name='accessibilityLabel'])):not(:has(JSXAttribute[name.name='aria-label'])):not(:has(JSXAttribute[name.name='accessible'][value.expression.value=false]))",
          message:
            "Interactive elements (TouchableOpacity, TouchableHighlight, Pressable) must have an 'accessibilityLabel' or 'aria-label' prop for screen reader support. Add accessible={false} only when the element is intentionally non-interactive to screen readers.",
        },
        {
          // Image without accessibilityLabel or role='presentation'
          selector:
            "JSXOpeningElement[name.name='Image']:not(:has(JSXAttribute[name.name='accessibilityLabel'])):not(:has(JSXAttribute[name.name='aria-label'])):not(:has(JSXAttribute[name.name='accessibilityRole'][value.value='none']))",
          message:
            "Image elements must have an 'accessibilityLabel' for meaningful images, or 'accessibilityRole=\"none\"' for decorative images.",
        },
      ],

      // -----------------------------------------------------------------------
      // General code quality (mobile-appropriate subset)
      // -----------------------------------------------------------------------
      "no-console": [
        "warn",
        {
          // Allow warn/error for structured logging; block plain log in prod
          allow: ["warn", "error"],
        },
      ],
    },
  },
];
