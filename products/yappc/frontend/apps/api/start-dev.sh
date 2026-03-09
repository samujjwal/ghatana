#!/bin/bash
cd "$(dirname "$0")"
PORT=7001 NODE_ENV=development npx tsx watch src/index.ts
