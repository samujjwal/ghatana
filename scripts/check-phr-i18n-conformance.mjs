#!/usr/bin/env node
import {
  printKernelProductI18nReport,
  scanKernelProductI18nConformance,
} from './lib/kernel-product-i18n-plugin.mjs';

const result = scanKernelProductI18nConformance({
  productLabel: 'PHR',
  scanDirs: [
    'products/phr/apps/web/src/pages',
    'products/phr/apps/mobile/src/screens',
  ],
  allowRegexes: [
    /PHR Nepal/,
  ],
});

printKernelProductI18nReport(result);
process.exit(result.violations.length === 0 ? 0 : 1);
