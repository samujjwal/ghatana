export type TutorPutorRole =
  | "student"
  | "parent"
  | "teacher"
  | "content_author"
  | "sme_reviewer"
  | "qa"
  | "admin"
  | "institution_admin"
  | "superadmin";

export type TutorPutorPermission =
  | "content.publish"
  | "assessment.grading.review"
  | "learner.data.self.read"
  | "learner.data.child.read"
  | "learner.data.class.read"
  | "parent.dashboard.read"
  | "instructor.class.dashboard.read"
  | "admin.export"
  | "privacy.export.self"
  | "privacy.delete.self"
  | "privacy.delete.process"
  | "lti.launch"
  | "lti.grade.passback";

export const ROLE_PERMISSION_MATRIX: Readonly<Record<TutorPutorRole, readonly TutorPutorPermission[]>> = Object.freeze({
  student: ["learner.data.self.read", "privacy.export.self", "privacy.delete.self", "lti.launch"],
  parent: ["learner.data.child.read", "parent.dashboard.read"],
  teacher: [
    "assessment.grading.review",
    "learner.data.class.read",
    "instructor.class.dashboard.read",
    "lti.launch",
    "lti.grade.passback",
  ],
  content_author: ["content.publish"],
  sme_reviewer: ["content.publish"],
  qa: ["content.publish"],
  admin: [
    "content.publish",
    "assessment.grading.review",
    "learner.data.class.read",
    "instructor.class.dashboard.read",
    "admin.export",
    "privacy.delete.process",
    "lti.launch",
    "lti.grade.passback",
  ],
  institution_admin: [
    "assessment.grading.review",
    "learner.data.class.read",
    "instructor.class.dashboard.read",
    "admin.export",
    "privacy.delete.process",
    "lti.launch",
    "lti.grade.passback",
  ],
  superadmin: [
    "content.publish",
    "assessment.grading.review",
    "learner.data.self.read",
    "learner.data.child.read",
    "learner.data.class.read",
    "parent.dashboard.read",
    "instructor.class.dashboard.read",
    "admin.export",
    "privacy.export.self",
    "privacy.delete.self",
    "privacy.delete.process",
    "lti.launch",
    "lti.grade.passback",
  ],
});

export function normalizeTutorPutorRole(role: string | null | undefined): TutorPutorRole | null {
  if (!role) return null;
  if (role === "creator" || role === "author") return "content_author";
  if (role === "reviewer") return "sme_reviewer";
  if (role === "institution") return "institution_admin";
  return role in ROLE_PERMISSION_MATRIX ? (role as TutorPutorRole) : null;
}

export function hasPermission(role: string | null | undefined, permission: TutorPutorPermission): boolean {
  const normalized = normalizeTutorPutorRole(role);
  if (!normalized) return false;
  return ROLE_PERMISSION_MATRIX[normalized].includes(permission);
}

export function rolesForPermission(permission: TutorPutorPermission): TutorPutorRole[] {
  return (Object.keys(ROLE_PERMISSION_MATRIX) as TutorPutorRole[]).filter((role) =>
    ROLE_PERMISSION_MATRIX[role].includes(permission),
  );
}
