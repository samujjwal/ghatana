#!/usr/bin/env node

/**
 * Kernel product contract TS/Java route lifecycle parity guard.
 *
 * The TypeScript route contract is consumed by web/YAPPC tooling while the Java
 * ProductContract is consumed by Kernel services. Both sides must support the
 * canonical lifecycle states used by product route contracts.
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = process.cwd();
const TS_ROUTE_CONTRACT = resolve(ROOT, 'platform/typescript/kernel-product-contracts/src/route/ProductRouteContract.ts');
const JAVA_PRODUCT_CONTRACT = resolve(ROOT, 'platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/contracts/ProductContract.java');
const REQUIRED_STATES = ['stable', 'preview', 'blocked', 'hidden', 'deferred', 'removed'];

const tsSource = readFileSync(TS_ROUTE_CONTRACT, 'utf-8');
const javaSource = readFileSync(JAVA_PRODUCT_CONTRACT, 'utf-8');
const failures = [];

for (const state of REQUIRED_STATES) {
  if (!tsSource.includes(`"${state}"`)) {
    failures.push(`TypeScript route contract is missing '${state}'`);
  }

  const javaState = state.toUpperCase();
  if (!new RegExp(`\\b${javaState}\\b`).test(javaSource)) {
    failures.push(`Java ProductContract.RouteState is missing '${javaState}'`);
  }
}

if (!javaSource.includes('route.state() != RouteState.ACTIVE && route.state() != RouteState.STABLE')) {
  failures.push('Java noLegacyMode must allow only ACTIVE/STABLE route states');
}

if (failures.length > 0) {
  console.error('FAIL: Kernel product contract schema parity violations found:');
  for (const failure of failures) {
    console.error(` - ${failure}`);
  }
  process.exit(1);
}

console.log(`PASS: Kernel product contract schema parity covers ${REQUIRED_STATES.join(', ')}`);
