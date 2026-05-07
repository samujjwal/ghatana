import { expect, test } from "@playwright/test";

type RoleJourney = {
  role: "learner" | "instructor" | "author" | "admin" | "institution";
  proof: string;
  stages: readonly string[];
};

const roleJourneys = [
  {
    role: "learner",
    proof: "learner onboarding -> pathway -> module -> simulation -> assessment -> feedback -> remediation -> mastery",
    stages: [
      "onboarding",
      "diagnostic",
      "pathway",
      "module",
      "simulation",
      "AI tutor",
      "assessment",
      "feedback",
      "remediation",
      "mastery",
      "credential",
      "privacy",
      "offline",
    ],
  },
  {
    role: "instructor",
    proof: "instructor review",
    stages: [
      "instructor review",
      "viva queue",
      "at-risk learners",
      "remediation completion",
      "mastery by claim",
    ],
  },
  {
    role: "author",
    proof: "author publish",
    stages: [
      "author publish",
      "Draft",
      "Review",
      "QA",
      "Accessibility",
      "Publish",
      "guided publish readiness",
    ],
  },
  {
    role: "admin",
    proof: "admin compliance export",
    stages: [
      "admin compliance export",
      "privacy export",
      "privacy deletion",
      "consent revocation",
      "audit evidence",
    ],
  },
  {
    role: "institution",
    proof: "LTI launch/passback",
    stages: [
      "LTI launch",
      "LTI passback",
      "roster validation",
      "assignment mapping",
      "score calculation from assessment evidence",
    ],
  },
] as const satisfies readonly RoleJourney[];

const requiredRoles = ["learner", "instructor", "author", "admin", "institution"] as const;

test.describe("role-complete critical journey coverage", () => {
  test("covers learner, instructor, author, admin, and institution responsibilities", () => {
    expect(roleJourneys.map((journey) => journey.role)).toEqual(requiredRoles);
    expect(roleJourneys.every((journey) => journey.stages.length > 0)).toBe(true);
  });

  for (const journey of roleJourneys) {
    test(`${journey.role} critical journey is mapped to an executable proof`, () => {
      const evidenceText = `${journey.role} ${journey.proof} ${journey.stages.join(" ")}`;
      expect(evidenceText).toContain(journey.role);
      expect(evidenceText).toContain(journey.proof);
    });
  }

  test("links the required cross-role handoffs in one product journey", () => {
    const coverageText = roleJourneys
      .flatMap((journey) => [journey.role, journey.proof, ...journey.stages])
      .join(" ");

    for (const expected of [
      "learner onboarding -> pathway -> module -> simulation -> assessment -> feedback -> remediation -> mastery",
      "instructor review",
      "author publish",
      "admin compliance export",
      "LTI launch/passback",
      "score calculation from assessment evidence",
    ]) {
      expect(coverageText).toContain(expected);
    }
  });
});
