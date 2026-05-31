#!/usr/bin/env node
import {
  printKernelProductAccessibilityReport,
  scanKernelProductAccessibility,
} from './lib/kernel-product-accessibility-plugin.mjs';

const result = scanKernelProductAccessibility({
  productLabel: 'PHR',
  scanDirs: [
    'products/phr/apps/web/src',
    'products/phr/apps/mobile/src',
  ],
});

printKernelProductAccessibilityReport(result);
process.exit(result.issues.length === 0 ? 0 : 1);
