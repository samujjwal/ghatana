#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

const repoRoot = process.cwd();

const violations = [];

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function sourceFile(relativePath) {
  const text = readText(relativePath);
  return ts.createSourceFile(relativePath, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
}

function add(file, message) {
  violations.push(`${file}: ${message}`);
}

function importedNames(file, moduleName) {
  const ast = sourceFile(file);
  const names = new Set();
  for (const statement of ast.statements) {
    if (!ts.isImportDeclaration(statement)) {
      continue;
    }
    if (statement.moduleSpecifier.getText(ast).replaceAll("'", '').replaceAll('"', '') !== moduleName) {
      continue;
    }
    const bindings = statement.importClause?.namedBindings;
    if (!bindings || !ts.isNamedImports(bindings)) {
      continue;
    }
    for (const element of bindings.elements) {
      names.add(element.name.text);
    }
  }
  return names;
}

function hasJsxElement(file, elementName) {
  const ast = sourceFile(file);
  let found = false;
  function visit(node) {
    if (
      (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) &&
      node.tagName.getText(ast) === elementName
    ) {
      found = true;
    }
    ts.forEachChild(node, visit);
  }
  visit(ast);
  return found;
}

function hasCall(file, callName) {
  const ast = sourceFile(file);
  let found = false;
  function visit(node) {
    if (ts.isCallExpression(node) && node.expression.getText(ast) === callName) {
      found = true;
    }
    ts.forEachChild(node, visit);
  }
  visit(ast);
  return found;
}

function hasFunctionDeclaration(file, functionName) {
  const ast = sourceFile(file);
  let found = false;
  function visit(node) {
    if (ts.isFunctionDeclaration(node) && node.name?.text === functionName) {
      found = true;
    }
    ts.forEachChild(node, visit);
  }
  visit(ast);
  return found;
}

function assertNoProductLocalEmptyState(file, allowedAdapter = false) {
  if (!hasFunctionDeclaration(file, 'EmptyState')) {
    return;
  }
  if (allowedAdapter && importedNames(file, '@ghatana/design-system').has('DesignEmptyState')) {
    return;
  }
  add(file, 'must use @ghatana/design-system EmptyState instead of product-local EmptyState JSX');
}

function assertImports(file, moduleName, names) {
  const imported = importedNames(file, moduleName);
  for (const name of names) {
    if (!imported.has(name)) {
      add(file, `must import ${name} from ${moduleName}`);
    }
  }
}

function assertProductShellWrapper(file) {
  assertImports(file, '@ghatana/product-shell', [
    'ProductHeaderUserMenu',
    'ProductShell',
    'ProductShellFooter',
    'createProductRoleSelectorConfig',
    'useProductEntitlements',
    'useProductShellConfig',
  ]);
  if (!hasJsxElement(file, 'ProductShell')) {
    add(file, 'must render <ProductShell>');
  }
  if (!hasJsxElement(file, 'ProductHeaderUserMenu')) {
    add(file, 'must render <ProductHeaderUserMenu>');
  }
  if (!hasJsxElement(file, 'ProductShellFooter')) {
    add(file, 'must render <ProductShellFooter>');
  }
  if (!hasCall(file, 'createProductRoleSelectorConfig')) {
    add(file, 'must build role selector props through createProductRoleSelectorConfig');
  }
  if (!hasCall(file, 'useProductShellConfig')) {
    add(file, 'must build shell config through useProductShellConfig');
  }
  if (!hasCall(file, 'useProductEntitlements')) {
    add(file, 'must hydrate shell routes through backend route entitlements');
  }
}

function assertRouteAccessEvaluator(file) {
  assertImports(file, '@ghatana/product-shell', ['createRouteAccessEvaluator']);
  if (!hasCall(file, 'createRouteAccessEvaluator')) {
    add(file, 'must build route access through createRouteAccessEvaluator');
  }
}

function assertAdoptsShell(file, elementName) {
  if (!hasJsxElement(file, elementName)) {
    add(file, `must render <${elementName}>`);
  }
}

function assertNoProductSpecificSharedComments() {
  const sharedFiles = [
    'platform/typescript/product-shell/src/components/ProductShell.tsx',
    'platform/typescript/product-shell/src/types.ts',
  ];
  const forbidden = /\b(AEP|Data Cloud)\b/;
  for (const file of sharedFiles) {
    if (forbidden.test(readText(file))) {
      add(file, 'shared platform source comments must stay product-neutral');
    }
  }
}

function assertProductShellStateSplit() {
  const file = 'platform/typescript/product-shell/src/components/ProductShell.tsx';
  const indexFile = 'platform/typescript/product-shell/src/index.ts';
  const source = readText(file);
  const indexSource = readText(indexFile);

  for (const exportName of ['useProductShellState', 'ProductShellLayout', 'ProductShell']) {
    if (!source.includes(`function ${exportName}`)) {
      add(file, `must keep ${exportName} as an explicit shell primitive`);
    }
    if (!indexSource.includes(exportName)) {
      add(indexFile, `must export ${exportName} from the public product-shell API`);
    }
  }

  const productShellBody = source.slice(source.indexOf('export function ProductShell('));
  if (!productShellBody.includes('useProductShellState()')) {
    add(file, 'ProductShell wrapper must delegate state ownership to useProductShellState');
  }
  if (!productShellBody.includes('<ProductShellLayout')) {
    add(file, 'ProductShell wrapper must delegate rendering to ProductShellLayout');
  }
}

if (!existsSync(path.join(repoRoot, 'products/phr/apps/web/src/layout/AppShell.tsx'))) {
  add('products/phr/apps/web/src/layout/AppShell.tsx', 'PHR app shell alias is missing');
} else {
  const phrAlias = readText('products/phr/apps/web/src/layout/AppShell.tsx');
  if (!phrAlias.includes('PhrProductShell as AppShell')) {
    add('products/phr/apps/web/src/layout/AppShell.tsx', 'must re-export PhrProductShell as AppShell');
  }
}

assertProductShellWrapper('products/phr/apps/web/src/layout/PhrProductShell.tsx');
assertProductShellWrapper('products/digital-marketing/ui/src/layout/DmosProductShell.tsx');
assertProductShellWrapper('products/flashit/client/web/src/components/FlashitProductShell.tsx');
assertRouteAccessEvaluator('products/phr/apps/web/src/phrRouteContracts.ts');
assertRouteAccessEvaluator('products/digital-marketing/ui/src/routeManifest.tsx');
assertRouteAccessEvaluator('products/flashit/client/web/src/routeAccess.ts');
assertAdoptsShell('products/digital-marketing/ui/src/App.tsx', 'DmosProductShell');
assertAdoptsShell('products/flashit/client/web/src/App.tsx', 'FlashitProductShell');
assertAdoptsShell('products/flashit/client/web/src/components/Layout.tsx', 'FlashitProductShell');
assertNoProductLocalEmptyState('products/flashit/client/web/src/pages/AnalyticsPage.tsx', true);
assertNoProductLocalEmptyState('products/flashit/client/web/src/pages/CollaborationPage.tsx');
assertProductShellStateSplit();
assertNoProductSpecificSharedComments();

if (violations.length > 0) {
  console.error('Shared product shell contract violations:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Shared product shell contract passed (AST + product-neutral docs).');
