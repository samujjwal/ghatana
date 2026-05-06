#!/usr/bin/env node

const requestedCommand = process.argv[2] ?? 'demo';

console.error(
  [
    `FlashIt demo-data command "${requestedCommand}" is intentionally disabled.`,
    'The previous scripts pointed at a non-existent products/flashit/scripts/demo-data workspace.',
    'Reintroduce demo-data only after a real product-owned package is added and registered in the workspace.',
  ].join(' '),
);

process.exit(1);
