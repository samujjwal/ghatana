#!/usr/bin/env node

import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const packageRoot = dirname(fileURLToPath(new URL('../package.json', import.meta.url)));
const source = join(packageRoot, 'src/schema/builder-document-v1.schema.json');
const target = join(packageRoot, 'dist/schema/builder-document-v1.schema.json');

mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
