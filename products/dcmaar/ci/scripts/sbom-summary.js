#!/usr/bin/env node
// SBOM summarizer: prints number of components and top N components
const fs = require("fs");
const path = require("path");

function usage() {
  console.log(
    "Usage: node scripts/sbom-summary.js <path-to-sbom.json> [topN] [--format=md|csv|json|text] [--out=path]"
  );
  process.exit(1);
}

const rawArgs = process.argv.slice(2);
if (rawArgs.length < 1) usage();

// Parse positional and optional args
const sbomPath = path.resolve(process.cwd(), rawArgs[0]);
let topN = 10;
let format = "text";
let outPath = null;
for (let i = 1; i < rawArgs.length; i++) {
  const a = rawArgs[i];
  if (a.startsWith("--format=")) format = a.split("=")[1];
  else if (a.startsWith("--out=")) outPath = a.split("=")[1];
  else if (!isNaN(Number(a))) topN = parseInt(a, 10);
  else if (a === "--help" || a === "-h") usage();
}

// Optional threshold check
let threshold = null;
let failIfAbove = false;
for (const a of rawArgs) {
  if (a.startsWith("--threshold=")) threshold = parseInt(a.split("=")[1], 10);
  if (a === "--fail-if-above") failIfAbove = true;
}

// Previous SBOM comparison
let previousPath = null;
let increaseThreshold = null;
let failIfIncreaseAbove = false;
let increasePercentThreshold = null;
let failIfIncreasePercentAbove = false;
for (const a of rawArgs) {
  if (a.startsWith("--previous=")) previousPath = a.split("=")[1];
  if (a.startsWith("--increase-threshold="))
    increaseThreshold = parseInt(a.split("=")[1], 10);
  if (a === "--fail-if-increase-above") failIfIncreaseAbove = true;
  if (a.startsWith("--increase-percent-threshold="))
    increasePercentThreshold = parseFloat(a.split("=")[1]);
  if (a === "--fail-if-increase-percent-above")
    failIfIncreasePercentAbove = true;
}

if (!fs.existsSync(sbomPath)) {
  console.error("SBOM file not found:", sbomPath);
  process.exit(2);
}

let raw;
try {
  raw = fs.readFileSync(sbomPath, "utf8");
} catch (e) {
  console.error("Failed to read SBOM file:", e.message);
  process.exit(2);
}

let json;
try {
  json = JSON.parse(raw);
} catch (e) {
  console.error("Failed to parse SBOM JSON:", e.message);
  process.exit(2);
}

const components = Array.isArray(json.components) ? json.components : [];
const specVersion = json.specVersion || json["specVersion"] || "unknown";

const header = `SBOM: ${sbomPath}\nSpecVersion: ${specVersion}\nComponent count: ${components.length}`;

function toText() {
  let out = header + "\n\nTop components:\n";
  components.slice(0, topN).forEach((c, i) => {
    const name = c.name || c.purl || "<unknown>";
    const ver = c.version || "-";
    const purl = c.purl || "-";
    out += `${i + 1}. ${name} ${ver}  (${purl})\n`;
  });
  return out;
}

function toMarkdown() {
  let md = `### SBOM Summary\n\n**SpecVersion:** ${specVersion}  
**Component count:** ${components.length}\n\n`;
  md += "| # | Name | Version | PURL |\n|---:|---|---|---|\n";
  components.slice(0, topN).forEach((c, i) => {
    const name = (c.name || c.purl || "").replace(/\|/g, "\\|");
    const ver = (c.version || "-").replace(/\|/g, "\\|");
    const purl = (c.purl || "-").replace(/\|/g, "\\|");
    md += `| ${i + 1} | ${name} | ${ver} | ${purl} |\n`;
  });
  return md;
}

function toCSV() {
  const rows = [["#", "name", "version", "purl"]];
  components.slice(0, topN).forEach((c, i) => {
    rows.push([
      String(i + 1),
      c.name || c.purl || "",
      c.version || "",
      c.purl || "",
    ]);
  });
  return rows
    .map((r) =>
      r.map((cell) => '"' + String(cell).replace(/"/g, '""') + '"').join(",")
    )
    .join("\n");
}

function toJSON() {
  return JSON.stringify(
    {
      specVersion,
      count: components.length,
      top: components
        .slice(0, topN)
        .map((c) => ({ name: c.name, version: c.version, purl: c.purl })),
    },
    null,
    2
  );
}

let output;
switch (format) {
  case "md":
    output = toMarkdown();
    break;
  case "csv":
    output = toCSV();
    break;
  case "json":
    output = toJSON();
    break;
  default:
    output = toText();
    break;
}

if (outPath) {
  try {
    fs.writeFileSync(path.resolve(process.cwd(), outPath), output, "utf8");
    console.log("Wrote summary to", outPath);
  } catch (e) {
    console.error("Failed to write summary file:", e.message);
    process.exit(3);
  }
} else {
  console.log(output);
}

// Threshold check: if threshold provided, warn or fail
if (threshold != null && !isNaN(Number(threshold))) {
  const count = components.length;
  if (count > threshold) {
    const msg = `SBOM component count ${count} exceeds threshold ${threshold}`;
    // Emit GitHub Actions warning annotation for visibility
    console.log(`::warning::${msg}`);
    if (failIfAbove) {
      console.error(msg);
      process.exit(4);
    }
  } else {
    console.log(
      `SBOM component count ${count} is within threshold ${threshold}`
    );
  }
}

// Previous SBOM increase check
if (previousPath) {
  const prevFull = path.resolve(process.cwd(), previousPath);
  if (!fs.existsSync(prevFull)) {
    console.log(
      `Previous SBOM not found at ${prevFull}; skipping increase check.`
    );
  } else {
    let prevRaw;
    try {
      prevRaw = fs.readFileSync(prevFull, "utf8");
    } catch (e) {
      console.error("Failed to read previous SBOM:", e.message);
      process.exit(5);
    }
    let prevJson;
    try {
      prevJson = JSON.parse(prevRaw);
    } catch (e) {
      console.error("Failed to parse previous SBOM JSON:", e.message);
      process.exit(5);
    }
    const prevComponents = Array.isArray(prevJson.components)
      ? prevJson.components
      : [];
    const prevCount = prevComponents.length;
    const newCount = components.length;
    const increase = newCount - prevCount;
    console.log(
      `Previous component count: ${prevCount}; current: ${newCount}; increase: ${increase}`
    );
    // Absolute increase check
    if (increaseThreshold != null && !isNaN(Number(increaseThreshold))) {
      if (increase > increaseThreshold) {
        const msg = `SBOM component count increased by ${increase} which exceeds increase-threshold ${increaseThreshold}`;
        console.log(`::warning::${msg}`);
        if (failIfIncreaseAbove) {
          console.error(msg);
          process.exit(6);
        }
      } else {
        console.log(
          `SBOM increase ${increase} is within threshold ${increaseThreshold}`
        );
      }
    }

    // Percent-based increase check
    if (
      increasePercentThreshold != null &&
      !isNaN(Number(increasePercentThreshold))
    ) {
      if (prevCount === 0) {
        if (newCount > 0) {
          const msg = `Previous SBOM had 0 components; current has ${newCount} — treated as 100% increase which exceeds percent-threshold ${increasePercentThreshold}`;
          console.log(`::warning::${msg}`);
          if (failIfIncreasePercentAbove) {
            console.error(msg);
            process.exit(7);
          }
        } else {
          console.log(
            "No components in previous and current SBOM; percent-increase check N/A."
          );
        }
      } else {
        const percent = (increase / prevCount) * 100;
        if (percent > increasePercentThreshold) {
          const msg = `SBOM component count increased by ${percent.toFixed(1)}% which exceeds increase-percent-threshold ${increasePercentThreshold}`;
          console.log(`::warning::${msg}`);
          if (failIfIncreasePercentAbove) {
            console.error(msg);
            process.exit(7);
          }
        } else {
          console.log(
            `SBOM percent increase ${percent.toFixed(1)}% is within threshold ${increasePercentThreshold}`
          );
        }
      }
    }
  }
}

process.exit(0);
