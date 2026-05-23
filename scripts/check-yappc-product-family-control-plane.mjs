#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const root = process.cwd();

function mustExist(relativePath, errors) {
  if (!existsSync(path.join(root, relativePath))) {
    errors.push(`Missing required file: ${relativePath}`);
  }
}

function mustContain(relativePath, token, label, errors) {
  const absolute = path.join(root, relativePath);
  if (!existsSync(absolute)) {
    errors.push(`Missing required file: ${relativePath}`);
    return;
  }

  const source = readFileSync(absolute, 'utf8');
  if (!source.includes(token)) {
    errors.push(`${relativePath} missing ${label}`);
  }
}

function main() {
  const errors = [];

  const controllerPath = 'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ProductFamilyControlPlaneController.java';
  const pagePath = 'products/yappc/frontend/web/src/pages/product-family/ProductFamilyControlPlanePage.tsx';
  const clientPath = 'products/yappc/frontend/web/src/clients/productFamilyClient.ts';

  mustExist(controllerPath, errors);
  mustExist(pagePath, errors);
  mustExist(clientPath, errors);

  mustContain(controllerPath, 'listAssets(HttpRequest request)', 'asset registry endpoint', errors);
  mustContain(controllerPath, 'promoteAsset(HttpRequest request)', 'asset promotion endpoint', errors);
  mustContain(controllerPath, 'listGuidedReuse(HttpRequest request)', 'guided reuse endpoint', errors);

  mustContain(clientPath, 'listProductAssets(filters', 'asset filter API adapter', errors);
  mustContain(clientPath, 'promoteProductAsset(', 'asset promotion API adapter', errors);
  mustContain(clientPath, 'listGuidedReuse(', 'guided reuse API adapter', errors);

  mustContain(pagePath, 'AssetRegistryPanel', 'asset registry UI panel', errors);
  mustContain(pagePath, 'TruthAndReusePanel', 'guided reuse UI panel', errors);
  mustContain(pagePath, 'compatibility', 'compatibility filter support', errors);
  mustContain(pagePath, 'maturity', 'maturity filter support', errors);
  mustContain(pagePath, 'reuseMode', 'reuse mode filter support', errors);
  mustContain(pagePath, 'promoteProductAsset', 'guided promotion action', errors);

  if (errors.length > 0) {
    console.error('YAPPC product-family control plane check failed:\n');
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  console.log('YAPPC product-family control plane check passed.');
}

main();
