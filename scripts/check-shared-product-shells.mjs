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
    'ProductShell',
    'useStableProductShellConfig',
  ]);
  if (!hasJsxElement(file, 'ProductShell')) {
    add(file, 'must render <ProductShell>');
  }
  if (!hasCall(file, 'useStableProductShellConfig')) {
    add(file, 'must build shell config through useStableProductShellConfig');
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
assertAdoptsShell('products/digital-marketing/ui/src/App.tsx', 'DmosProductShell');
assertAdoptsShell('products/flashit/client/web/src/App.tsx', 'FlashitProductShell');
assertAdoptsShell('products/flashit/client/web/src/components/Layout.tsx', 'FlashitProductShell');
assertNoProductSpecificSharedComments();

if (violations.length > 0) {
  console.error('Shared product shell contract violations:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('Shared product shell contract passed (AST + product-neutral docs).');
