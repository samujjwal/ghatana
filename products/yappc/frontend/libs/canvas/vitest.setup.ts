/**
 * @doc.type test-setup
 * @doc.purpose Vitest setup file with extended matchers
 * @doc.layer product
 * @doc.pattern Test Configuration
 */

import { expect } from 'vitest';
import * as matchers from '@testing-library/jest-dom/matchers';

// Extend Vitest's expect with jest-dom matchers
expect.extend(matchers);
