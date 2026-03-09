#!/usr/bin/env node
// Simple permission linter that checks a manifest-like JSON for disallowed perms
const fs = require("fs");
// path not needed currently
const allowed = new Set([
  "agent:read",
  "agent:write",
  "agent:status",
  "agent:metrics",
  // add allowed permissions here
]);

function main() {
  const file = process.argv[2] || "services/extension/manifest.json";
  const allowFile = process.argv[3] || "tools/permission-linter/allowlist.json";
  let localAllowed = new Set(allowed);
  if (fs.existsSync(allowFile)) {
    try {
      const list = JSON.parse(fs.readFileSync(allowFile, "utf8"));
      if (Array.isArray(list)) list.forEach((p) => localAllowed.add(p));
    } catch (e) {
      console.warn("Failed to load allowlist", allowFile, e);
    }
  }

  if (!fs.existsSync(file)) {
    console.error("Manifest not found:", file);
    process.exit(2);
  }
  const manifest = JSON.parse(fs.readFileSync(file, "utf8"));
  const perms = manifest.permissions || [];
  const bad = perms.filter((p) => !localAllowed.has(p));
  if (bad.length) {
    console.error("Disallowed permissions found:", bad);
    process.exit(1);
  }
  console.log("Permissions OK");
}

main();
