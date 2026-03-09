#!/usr/bin/env node
/**
 * Mock Config Server
 * - GET  /config     => returns current config
 * - POST /validate   => validates payload against ConfigEnvelope.json (AJV)
 * - POST /apply      => validates and persists config, returns changes
 * - POST /audit      => accepts audit entries and persists them
 * - GET  /audit      => returns audit entries
 *
 * Data is persisted atomically to data.json in the same folder.
 */
const path = require("path");
const fs = require("fs");
const express = require("express");
// body-parser is not required in modern Express; use built-in json parser
const Ajv = require("ajv");

// Default port changed to 9001 to align with CI/integration runner
const PORT = process.env.PORT || 9001;
const DATA_FILE = path.join(__dirname, "data.json");
const SCHEMA_FILE = path.join(
  __dirname,
  "..",
  "..",
  "shared",
  "test-fixtures",
  "config-envelope",
  "schemas",
  "ConfigEnvelope.json"
);

function loadData() {
  try {
    if (!fs.existsSync(DATA_FILE)) {
      const init = { config: null, audits: [] };
      fs.writeFileSync(DATA_FILE, JSON.stringify(init, null, 2));
      return init;
    }
    return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
  } catch (e) {
    console.error("Failed to load data file", e);
    return { config: null, audits: [] };
  }
}

function saveData(data) {
  // atomic write
  const tmp = DATA_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, DATA_FILE);
}

const app = express();
app.use(express.json({ limit: "2mb" }));

let schema = null;
try {
  schema = fs.existsSync(SCHEMA_FILE)
    ? JSON.parse(fs.readFileSync(SCHEMA_FILE, "utf8"))
    : null;
} catch (e) {
  console.error("Failed to read schema file", e);
}
const ajv = new Ajv({ strict: false, validateSchema: false });
let validate = null;
if (schema) {
  // Avoid Ajv attempting to resolve $schema meta-schemas (some Ajv versions don't include newer metas)
  if (schema.$schema) delete schema.$schema;
  validate = ajv.compile(schema);
}

app.get("/config", (req, res) => {
  const data = loadData();
  res.json(data.config ?? {});
});

app.post("/validate", (req, res) => {
  const payload = req.body;
  if (!validate)
    return res.status(500).json({ error: "Schema not found on server" });
  const ok = validate(payload);
  res.json({ valid: ok, errors: ok ? [] : validate.errors });
});

app.post("/apply", (req, res) => {
  const payload = req.body;
  if (!validate)
    return res.status(500).json({ error: "Schema not found on server" });
  const ok = validate(payload);
  if (!ok) return res.status(400).json({ ok: false, errors: validate.errors });

  const data = loadData();
  const oldConfig = data.config || null;
  data.config = payload;
  // record an internal audit
  const audit = {
    id: `srv_${Date.now()}`,
    timestamp: new Date().toISOString(),
    user: "mock-server",
    action: "apply",
    summary: "Server applied config",
    details: { old: oldConfig, new: payload },
  };
  data.audits.unshift(audit);
  saveData(data);

  res.json({
    ok: true,
    applied: true,
    audit,
    changes: { old: oldConfig, new: payload },
  });
});

app.post("/audit", (req, res) => {
  const entry = req.body;
  const data = loadData();
  // basic validation
  if (!entry || !entry.id)
    return res.status(400).json({ error: "invalid entry" });
  data.audits.unshift(entry);
  saveData(data);
  res.json({ ok: true });
});

app.get("/audit", (req, res) => {
  const data = loadData();
  res.json(data.audits || []);
});

app.listen(PORT, () => {
  console.log(`Mock config server listening on http://localhost:${PORT}`);
  if (!schema) console.warn("ConfigEnvelope schema not found at", SCHEMA_FILE);
});
