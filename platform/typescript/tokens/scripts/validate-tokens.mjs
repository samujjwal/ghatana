#!/usr/bin/env node
const { validateTokens, tokens } = await import('../dist/index.js');

const result = validateTokens(tokens);

if (!result.success) {
  console.error('❌ Design token validation failed:');
  result.errors?.forEach((error) => console.error(`  • ${error}`));
  process.exit(1);
}

console.log('✅ Design tokens validated successfully.');
