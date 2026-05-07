#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const productRoot = path.resolve("products/tutorputor");
const openApiPath = path.join(productRoot, "api/tutorputor-api.openapi.yaml");
const contractPath = path.join(productRoot, "contracts/v1/openapi.ts");
const gatewayPath = path.join(productRoot, "apps/api-gateway/src/createServer.ts");

const openApi = fs.readFileSync(openApiPath, "utf8");
const contractSource = fs.readFileSync(contractPath, "utf8");
const gatewaySource = fs.readFileSync(gatewayPath, "utf8");

const httpMethods = new Set([
  "get",
  "post",
  "put",
  "patch",
  "delete",
  "options",
  "head",
]);

function exportedTypeExists(typeName) {
  if (typeName === null || typeName === "null") {
    return true;
  }
  return new RegExp(`export\\s+(interface|type)\\s+${typeName}\\b`).test(
    contractSource,
  );
}

function serviceMethodExists(interfaceName, methodName) {
  const interfaceMatch = contractSource.match(
    new RegExp(`export\\s+interface\\s+${interfaceName}\\s*{([\\s\\S]*?)\\n}`),
  );
  if (!interfaceMatch) {
    return false;
  }
  return new RegExp(`\\b${methodName}\\s*\\(`).test(interfaceMatch[1]);
}

function operationBindingExists(operationId, field, expectedValue) {
  const operationMatch = contractSource.match(
    new RegExp(`${operationId}:\\s*{([\\s\\S]*?)\\n\\s*},`),
  );
  if (!operationMatch) {
    return false;
  }
  const value =
    expectedValue === null || expectedValue === "null"
      ? "null"
      : `"${expectedValue}"`;
  return new RegExp(`${field}:\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`).test(
    operationMatch[1],
  );
}

function parseOperations(yaml) {
  const operations = [];
  const lines = yaml.split(/\r?\n/);
  let currentPath = null;
  let currentMethod = null;
  let currentOperation = null;
  let inContract = false;
  let contractIndent = 0;

  function flush() {
    if (currentOperation) {
      operations.push(currentOperation);
    }
    currentOperation = null;
    inContract = false;
  }

  for (const line of lines) {
    const pathMatch = line.match(/^  (\/[^:]+):\s*$/);
    if (pathMatch) {
      flush();
      currentPath = pathMatch[1];
      currentMethod = null;
      continue;
    }

    const methodMatch = line.match(/^    ([a-z]+):\s*$/);
    if (methodMatch && httpMethods.has(methodMatch[1])) {
      flush();
      currentMethod = methodMatch[1];
      currentOperation = {
        path: currentPath,
        method: currentMethod,
        operationId: null,
        contract: {},
      };
      continue;
    }

    if (!currentOperation) {
      continue;
    }

    const operationIdMatch = line.match(/^\s{6}operationId:\s*(\S+)\s*$/);
    if (operationIdMatch) {
      currentOperation.operationId = operationIdMatch[1];
      continue;
    }

    const contractMatch = line.match(/^(\s*)x-tutorputor-contract:\s*$/);
    if (contractMatch) {
      inContract = true;
      contractIndent = contractMatch[1].length;
      continue;
    }

    if (inContract) {
      const indent = line.match(/^(\s*)/)?.[1].length ?? 0;
      if (line.trim() === "") {
        continue;
      }
      if (indent <= contractIndent) {
        inContract = false;
        continue;
      }
      const fieldMatch = line.match(/^\s+([A-Za-z0-9_]+):\s*(.*)$/);
      if (fieldMatch) {
        const rawValue = fieldMatch[2].trim();
        currentOperation.contract[fieldMatch[1]] =
          rawValue === "null" ? null : rawValue.replace(/^["']|["']$/g, "");
      }
    }
  }
  flush();
  return operations.filter((operation) => operation.operationId);
}

function parseComponentSchemas(yaml) {
  const schemas = [];
  const lines = yaml.split(/\r?\n/);
  let inSchemas = false;
  for (const line of lines) {
    if (/^  schemas:\s*$/.test(line)) {
      inSchemas = true;
      continue;
    }
    if (inSchemas && /^  [A-Za-z]+:\s*$/.test(line)) {
      break;
    }
    const schemaMatch = inSchemas ? line.match(/^    ([A-Za-z0-9_]+):\s*$/) : null;
    if (schemaMatch) {
      schemas.push(schemaMatch[1]);
    }
  }
  return schemas;
}

const failures = [];
const operations = parseOperations(openApi);
const schemas = parseComponentSchemas(openApi);

for (const schemaName of schemas) {
  if (!exportedTypeExists(schemaName)) {
    failures.push(`OpenAPI schema ${schemaName} has no export in contracts/v1/openapi.ts`);
  }
}

for (const operation of operations) {
  const requiredFields = [
    "requestType",
    "responseType",
    "serviceInterface",
    "serviceMethod",
    "backendOwner",
    "typedClient",
  ];
  for (const field of requiredFields) {
    if (!(field in operation.contract)) {
      failures.push(`${operation.method.toUpperCase()} ${operation.path} missing x-tutorputor-contract.${field}`);
    }
  }

  const { requestType, responseType, serviceInterface, serviceMethod, backendOwner, typedClient } =
    operation.contract;

  if (!exportedTypeExists(requestType)) {
    failures.push(`${operation.operationId} requestType ${requestType} is not exported`);
  }
  if (!exportedTypeExists(responseType)) {
    failures.push(`${operation.operationId} responseType ${responseType} is not exported`);
  }
  if (!serviceMethodExists(serviceInterface, serviceMethod)) {
    failures.push(`${operation.operationId} service method ${serviceInterface}.${serviceMethod} is missing`);
  }

  for (const [label, relativePath] of [
    ["backendOwner", backendOwner],
    ["typedClient", typedClient],
  ]) {
    if (!relativePath || !fs.existsSync(path.join(productRoot, relativePath))) {
      failures.push(`${operation.operationId} ${label} file does not exist: ${relativePath}`);
    }
  }

  const bindingChecks = {
    method: operation.method,
    path: operation.path,
    requestType,
    responseType,
    serviceInterface,
    serviceMethod,
  };
  for (const [field, value] of Object.entries(bindingChecks)) {
    if (!operationBindingExists(operation.operationId, field, value)) {
      failures.push(`${operation.operationId} missing matching TUTORPUTOR_OPENAPI_OPERATION_BINDINGS.${field}`);
    }
  }
}

if (!gatewaySource.includes("setupPlatform(app)")) {
  failures.push("apps/api-gateway/src/createServer.ts must embed the canonical platform backend via setupPlatform(app)");
}

if (failures.length > 0) {
  console.error("API contract drift validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log(
  `API contract drift validation passed (${operations.length} operations, ${schemas.length} schemas).`,
);
