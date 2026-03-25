/**
 * Vitest/jsdom global setup for @ghatana/design-system tests.
 *
 * Extends the `expect` object with @testing-library/jest-dom matchers
 * (e.g. `toBeInTheDocument`, `toHaveAccessibleName`, etc.).
 */
import '@testing-library/jest-dom';
