import { describe, expect, it } from "vitest";
import {
  hasPermission,
  rolesForPermission,
  type TutorPutorPermission,
  type TutorPutorRole,
} from "./permissionPolicy";

const matrix: Array<{
  permission: TutorPutorPermission;
  allowed: TutorPutorRole[];
  denied: TutorPutorRole[];
}> = [
  {
    permission: "content.publish",
    allowed: ["content_author", "sme_reviewer", "qa", "admin", "superadmin"],
    denied: ["student", "parent", "teacher"],
  },
  {
    permission: "assessment.grading.review",
    allowed: ["teacher", "admin", "institution_admin", "superadmin"],
    denied: ["student", "parent", "content_author"],
  },
  {
    permission: "learner.data.self.read",
    allowed: ["student", "superadmin"],
    denied: ["parent", "content_author", "qa"],
  },
  {
    permission: "learner.data.child.read",
    allowed: ["parent", "superadmin"],
    denied: ["student", "teacher", "content_author"],
  },
  {
    permission: "learner.data.class.read",
    allowed: ["teacher", "admin", "institution_admin", "superadmin"],
    denied: ["student", "parent", "content_author"],
  },
  {
    permission: "parent.dashboard.read",
    allowed: ["parent", "superadmin"],
    denied: ["student", "teacher", "qa"],
  },
  {
    permission: "instructor.class.dashboard.read",
    allowed: ["teacher", "admin", "institution_admin", "superadmin"],
    denied: ["student", "parent", "qa"],
  },
  {
    permission: "admin.export",
    allowed: ["admin", "institution_admin", "superadmin"],
    denied: ["student", "parent", "teacher", "content_author"],
  },
  {
    permission: "privacy.delete.process",
    allowed: ["admin", "institution_admin", "superadmin"],
    denied: ["student", "parent", "teacher", "content_author"],
  },
  {
    permission: "lti.launch",
    allowed: ["student", "teacher", "admin", "institution_admin", "superadmin"],
    denied: ["parent", "content_author", "qa"],
  },
  {
    permission: "lti.grade.passback",
    allowed: ["teacher", "admin", "institution_admin", "superadmin"],
    denied: ["student", "parent", "content_author", "qa"],
  },
];

describe("TutorPutor permission policy", () => {
  for (const row of matrix) {
    it(`enforces ${row.permission}`, () => {
      for (const role of row.allowed) {
        expect(hasPermission(role, row.permission), `${role} should be allowed`).toBe(true);
      }
      for (const role of row.denied) {
        expect(hasPermission(role, row.permission), `${role} should be denied`).toBe(false);
      }
      expect(rolesForPermission(row.permission)).toEqual(expect.arrayContaining(row.allowed));
    });
  }

  it("maps legacy author/reviewer role names to canonical roles", () => {
    expect(hasPermission("creator", "content.publish")).toBe(true);
    expect(hasPermission("author", "content.publish")).toBe(true);
    expect(hasPermission("reviewer", "content.publish")).toBe(true);
  });
});
