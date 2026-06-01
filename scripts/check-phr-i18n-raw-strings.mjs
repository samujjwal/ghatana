#!/usr/bin/env node

/**
 * PHR i18n raw string check.
 *
 * Uses the shared Kernel product i18n scanner so PHR web/mobile checks do not
 * maintain a second ad hoc JSX parser.
 *
 * @doc.type script
 * @doc.purpose i18n conformance check
 * @doc.layer infrastructure
 */

import {
  printKernelProductI18nReport,
  scanKernelProductI18nConformance,
} from './lib/kernel-product-i18n-plugin.mjs';

const result = scanKernelProductI18nConformance({
  productLabel: 'PHR',
  scanDirs: [
    'products/phr/apps/web/src/components',
    'products/phr/apps/web/src/layout',
    'products/phr/apps/web/src/pages',
    'products/phr/apps/mobile/src/screens',
  ],
  allowRegexes: [
    /PHR Nepal/,
  ],
});

printKernelProductI18nReport(result);
process.exit(result.violations.length === 0 ? 0 : 1);
