import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const root = join(process.cwd(), "products", "tutorputor");
const serviceRoot = join(root, "services", "tutorputor-content-generation");
const forbiddenFiles = [
  join(serviceRoot, "src", "fix_getters.sh"),
];

const forbiddenPatterns = [
  {
    pattern: /fix_getters\.sh/,
    message: "content generation must not depend on the removed getter repair script",
  },
  {
    pattern: /PlatformContentGenerator\.java\s*>\s*temp\.java/,
    message: "content generation must not rewrite PlatformContentGenerator.java after generation",
  },
];

const ignoredSegments = new Set(["node_modules", "build", ".gradle", ".git"]);
const invocationRoots = [
  join(root, ".gitea"),
  join(root, "scripts"),
  join(serviceRoot),
];
const invocationFilePattern = /\.(?:gradle|kts|sh|ps1|cmd|bat|json|ya?ml)$/;

function walkFiles(directory) {
  const entries = readdirSync(directory, { withFileTypes: true });
  return entries.flatMap((entry) => {
    if (ignoredSegments.has(entry.name)) {
      return [];
    }

    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) {
      return walkFiles(fullPath);
    }

    return entry.isFile() ? [fullPath] : [];
  });
}

const failures = [];

for (const file of forbiddenFiles) {
  if (existsSync(file)) {
    failures.push(`Forbidden repair script still exists: ${relative(root, file)}`);
  }
}

for (const invocationRoot of invocationRoots) {
  if (!existsSync(invocationRoot)) {
    continue;
  }

  for (const file of walkFiles(invocationRoot)) {
    if (!statSync(file).isFile()) {
      continue;
    }

    const relativePath = relative(root, file).replaceAll("\\", "/");
    if (relativePath === "scripts/validate-content-generation-source.mjs") {
      continue;
    }
    if (!invocationFilePattern.test(relativePath)) {
      continue;
    }

    const content = readFileSync(file, "utf8");
    for (const { pattern, message } of forbiddenPatterns) {
      if (pattern.test(content)) {
        failures.push(`${relativePath}: ${message}`);
      }
    }
  }
}

if (failures.length > 0) {
  console.error("Content-generation source validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Content-generation source validation passed.");
