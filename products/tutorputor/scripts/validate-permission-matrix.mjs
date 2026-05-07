#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(process.cwd(), "products/tutorputor");
const errors = [];

const read = (relativePath) => {
  const fullPath = path.join(root, relativePath);
  if (!fs.existsSync(fullPath)) {
    errors.push(`Missing required permission file: ${relativePath}`);
    return "";
  }
  return fs.readFileSync(fullPath, "utf8");
};

const policy = read("services/tutorputor-platform/src/core/authz/permissionPolicy.ts");
const policyTest = read("services/tutorputor-platform/src/core/authz/permissionPolicy.test.ts");
const requestContext = read("services/tutorputor-platform/src/core/http/requestContext.ts");
const complianceRoutes = read("services/tutorputor-platform/src/modules/compliance/routes.ts");
const contracts = read("contracts/v1/types.ts");
const ci = read(".gitea/workflows/tutorputor-ci.yml");

for (const role of [
  "student",
  "parent",
  "teacher",
  "content_author",
  "sme_reviewer",
  "qa",
  "admin",
  "institution_admin",
  "superadmin",
]) {
  if (!policy.includes(role)) errors.push(`Permission policy missing role ${role}.`);
  if (!policyTest.includes(role)) errors.push(`Permission matrix tests missing role ${role}.`);
  if (!contracts.includes(role)) errors.push(`Shared UserRole contract missing role ${role}.`);
}

for (const permission of [
  "content.publish",
  "assessment.grading.review",
  "learner.data.self.read",
  "learner.data.child.read",
  "learner.data.class.read",
  "parent.dashboard.read",
  "instructor.class.dashboard.read",
  "admin.export",
  "privacy.delete.process",
  "lti.launch",
  "lti.grade.passback",
]) {
  if (!policy.includes(permission)) errors.push(`Permission policy missing ${permission}.`);
  if (!policyTest.includes(permission)) errors.push(`Permission matrix tests missing ${permission}.`);
}

if (!requestContext.includes("requirePermission")) {
  errors.push("requestContext does not expose backend permission enforcement.");
}

for (const wiredPermission of ["admin.export", "privacy.delete.process"]) {
  if (!complianceRoutes.includes(wiredPermission)) {
    errors.push(`Representative compliance route is not wired to ${wiredPermission}.`);
  }
}

if (!ci.includes("validate-permission-matrix.mjs")) {
  errors.push("TutorPutor CI does not run the permission matrix validator.");
}

if (errors.length > 0) {
  console.error("Permission matrix validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Permission matrix covers product roles, publish/grading/learner/parent/instructor/admin/LTI permissions, backend enforcement, tests, and CI validation.");
