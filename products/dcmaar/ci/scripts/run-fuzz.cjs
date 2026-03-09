#!/usr/bin/env node
const { spawnSync } = require('child_process');
const iterations = process.env.FUZZ_ITERATIONS || '200';
console.log(`Delegating DER fuzz harness to services/extension/scripts/run-fuzz.cjs with ${iterations} iterations`);
const res = spawnSync('node', ['services/extension/scripts/run-fuzz.cjs'], {
  env: { ...process.env, FUZZ_ITERATIONS: iterations },
  stdio: 'inherit',
  shell: true,
});
process.exit(res.status || 0);
