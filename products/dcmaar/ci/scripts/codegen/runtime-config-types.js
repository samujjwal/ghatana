#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { compile } = require('json-schema-to-typescript');

const ROOT = path.resolve(__dirname, '../../..');
const SCHEMA_PATH = path.join(ROOT, 'contracts/json-schema/runtime_config.schema.json');
const OUT_DIR = path.join(ROOT, 'libs/typescript/types/src/generated');
const OUT_FILE = path.join(OUT_DIR, 'runtime-config.ts');

async function main() {
    const schemaSrc = await fs.promises.readFile(SCHEMA_PATH, 'utf-8');
    const schema = JSON.parse(schemaSrc);

    const ts = await compile(schema, 'DcmaarRuntimeConfiguration', {
        bannerComment: [
            '// AUTO-GENERATED FILE. DO NOT EDIT.',
            '// Generated from contracts/json-schema/runtime_config.schema.json.',
            '// To regenerate, run: pnpm run codegen:runtime-config',
            '',
        ].join('\n'),
        additionalProperties: false,
    });

    await fs.promises.mkdir(OUT_DIR, { recursive: true });
    await fs.promises.writeFile(OUT_FILE, ts);

    console.log(`✅ Generated TypeScript types at ${path.relative(ROOT, OUT_FILE)}`);
}

main().catch((err) => {
    console.error('❌ Failed to generate runtime config types:', err);
    process.exit(1);
});
