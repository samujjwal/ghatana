#!/usr/bin/env node
// Simple integration helper: posts the sample config to mock server /validate and /apply
const fs = require("fs");
const _path = require("path");
// Use global fetch on Node 18+ when available, otherwise fall back to node-fetch
let fetch;
try {
  fetch = globalThis.fetch;
} catch {}
if (!fetch) {
  try {
    fetch = require("node-fetch");
  } catch {
    console.error(
      "fetch is not available. Please run this on Node 18+ or install node-fetch."
    );
    process.exit(2);
  }
}

const sample =
  process.argv[2] || "shared/test-fixtures/config-envelope/samples/sample-config-envelope.json";
// default to 9001 to match the runner and workflows
const url = process.env.MOCK_URL || "http://localhost:9001";

async function main() {
  const body = JSON.parse(fs.readFileSync(sample, "utf8"));
  console.log("Validating...");
  let v;
  try {
    const res = await fetch(url + "/validate", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    v = await res.json();
  } catch (err) {
    console.error(
      "Failed to contact mock server at",
      url,
      "— is it running?",
      err.message || err
    );
    process.exit(3);
  }
  console.log("Validate:", v);
  if (!v.valid) process.exit(3);
  console.log("Applying...");
  try {
    const res2 = await fetch(url + "/apply", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    const a = await res2.json();
    console.log("Apply:", a);
  } catch (err) {
    console.error("Failed to POST /apply to mock server:", err.message || err);
    process.exit(4);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
