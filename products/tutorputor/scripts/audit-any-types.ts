#!/usr/bin/env node
/**
 * @doc.type script
 * @doc.purpose Audit explicit any usage in TutorPutor TypeScript sources
 * @doc.layer tooling
 */

import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

interface AnyUsage {
  file: string;
  line: number;
  column: number;
  context: string;
  type: "explicit_any" | "as_any";
}

const ROOT = path.resolve(
  process.cwd(),
  "products/tutorputor/services/tutorputor-platform/src",
);
const THRESHOLD = Number.parseInt(process.env.ANY_TYPE_THRESHOLD ?? "100", 10);

function collectTsFiles(dir: string, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);

    if (stat.isDirectory()) {
      collectTsFiles(fullPath, acc);
      continue;
    }

    if (fullPath.endsWith(".ts") || fullPath.endsWith(".tsx")) {
      acc.push(fullPath);
    }
  }

  return acc;
}

function scanFile(file: string): AnyUsage[] {
  const lines = readFileSync(file, "utf8").split("\n");
  const usages: AnyUsage[] = [];

  lines.forEach((line, index) => {
    const explicitAny = line.match(/:\s*any\b/);
    if (explicitAny?.index !== undefined) {
      usages.push({
        file,
        line: index + 1,
        column: explicitAny.index + 1,
        context: line.trim(),
        type: "explicit_any",
      });
    }

    const castAny = line.match(/\bas\s+any\b/);
    if (castAny?.index !== undefined) {
      usages.push({
        file,
        line: index + 1,
        column: castAny.index + 1,
        context: line.trim(),
        type: "as_any",
      });
    }
  });

  return usages;
}

function main(): void {
  const files = collectTsFiles(ROOT);
  const usages = files.flatMap(scanFile);

  console.log("\n=== Any Type Audit Report ===\n");
  console.log(`Total files scanned: ${files.length}`);
  console.log(`Total explicit 'any' usages: ${usages.length}`);

  usages.slice(0, 50).forEach((usage) => {
    console.log(
      `${path.relative(process.cwd(), usage.file)}:${usage.line}:${usage.column} [${usage.type}] ${usage.context}`,
    );
  });

  if (usages.length > THRESHOLD) {
    console.error(
      `\nFAILED: ${usages.length} explicit 'any' usages found (threshold: ${THRESHOLD})`,
    );
    process.exit(1);
  }

  console.log(`\nPASSED: ${usages.length} explicit 'any' usages within threshold`);
}

main();
