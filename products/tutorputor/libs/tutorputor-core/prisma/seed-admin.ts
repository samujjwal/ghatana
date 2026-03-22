/**
 * Seed script for TutorPutor Admin E2E testing
 *
 * Creates comprehensive test data for all admin journeys:
 * - Tenant with settings
 * - Users with various roles
 * - Modules and enrollments
 * - Classrooms with members
 * - SSO providers
 * - Compliance requests
 * - Learning events for analytics
 * - Audit logs
 *
 * @doc.type script
 * @doc.purpose Seed database for admin E2E testing
 * @doc.layer product
 * @doc.pattern Script
 */

import { createPrismaClient, seedLearningUnits, seedSimulations, DEFAULT_TENANT_ID } from "../src/index.js";

let prisma: any;

// =============================================================================
// Constants
// =============================================================================

const TENANT_ID = process.env.TUTORPUTOR_DEFAULT_TENANT_ID ?? DEFAULT_TENANT_ID;
const ADMIN_USER_ID = "user-admin-001";
const TEACHER_USER_ID = "user-teacher-001";
const CREATOR_USER_ID = "user-creator-001";

// =============================================================================
// Seed Functions
// =============================================================================

async function seedTenant() {
  console.log("🏢 Seeding tenant...");

  await prisma.tenant.upsert({
    where: { id: TENANT_ID },
    update: {},
    create: {
      id: TENANT_ID,
      name: "TutorPutor Demo Institution",
      subdomain: "demo",
      subscriptionTier: "ENTERPRISE",
      createdAt: new Date("2024-01-01"),
    },
  });

  // Seed tenant settings
  await prisma.tenantSettings.upsert({
    where: { tenantId: TENANT_ID },
    update: {},
    create: {
      tenantId: TENANT_ID,
      allowPublicRegistration: false,
      requireEmailVerification: true,
      defaultUserRole: "student",
      maxUsersPerClassroom: 50,
      enabledFeatures: JSON.stringify(["ai_tutor", "vr_labs", "peer_tutoring", "gamification"]),
      enabledDomainPacks: JSON.stringify(["physics", "chemistry", "mathematics", "computer_science"]),
      simulationQuotas: JSON.stringify({
        maxConcurrentSessions: 25,
        monthlyRuns: 5000,
      }),
    },
  });

  console.log("✅ Tenant seeded");
}

async function seedUsers() {
  console.log("👥 Seeding users...");

  const users = [
    // Admin users
    {
      id: ADMIN_USER_ID,
      email: "admin@demo.tutorputor.com",
      displayName: "Sarah Admin",
      role: "admin",
    },
    {
      id: "user-admin-002",
      email: "it-admin@demo.tutorputor.com",
      displayName: "Mike IT Admin",
      role: "admin",
    },
    // Teachers
    {
      id: TEACHER_USER_ID,
      email: "teacher@demo.tutorputor.com",
      displayName: "Dr. Emily Teacher",
      role: "teacher",
    },
    {
      id: "user-teacher-002",
      email: "physics.teacher@demo.tutorputor.com",
      displayName: "Prof. James Physics",
      role: "teacher",
    },
    {
      id: "user-teacher-003",
      email: "math.teacher@demo.tutorputor.com",
      displayName: "Ms. Lisa Math",
      role: "teacher",
    },
    // Creators
    {
      id: CREATOR_USER_ID,
      email: "creator@demo.tutorputor.com",
      displayName: "Alex Creator",
      role: "creator",
    },
    {
      id: "user-creator-002",
      email: "content.creator@demo.tutorputor.com",
      displayName: "Jordan Content",
      role: "creator",
    },
    // Demo user for auth testing
    {
      id: "user-demo-001",
      email: "demo@tutorputor.com",
      displayName: "Demo User",
      role: "instructor",
    },
    // Students (50 students for realistic data)
    ...Array.from({ length: 50 }, (_, i) => ({
      id: `user-student-${String(i + 1).padStart(3, "0")}`,
      email: `student${i + 1}@demo.tutorputor.com`,
      displayName: `Student ${i + 1}`,
      role: "student",
    })),
  ];

  for (const user of users) {
    await prisma.user.upsert({
      where: { id: user.id },
      update: {},
      create: {
        ...user,
        tenantId: TENANT_ID,
      },
    });
  }

  console.log(`✅ ${users.length} users seeded`);
}

async function seedModules() {
  console.log("📚 Seeding modules...");

  const modules = [
    {
      id: "module-physics-001",
      slug: "intro-mechanics",
      title: "Introduction to Mechanics",
      description: "Learn the fundamentals of classical mechanics",
      domain: "SCIENCE" as const,
      difficulty: "INTRO" as const,
      estimatedTimeMinutes: 45,
      tags: ["physics", "mechanics", "forces"],
      status: "PUBLISHED" as const,
    },
    {
      id: "module-physics-002",
      slug: "newtons-laws",
      title: "Newton's Laws of Motion",
      description: "Deep dive into Newton's three laws",
      domain: "SCIENCE",
      difficulty: "INTERMEDIATE",
      estimatedTimeMinutes: 60,
      tags: ["physics", "newton", "motion"],
      status: "PUBLISHED",
    },
    {
      id: "module-physics-003",
      slug: "energy-conservation",
      title: "Energy Conservation",
      description: "Understanding energy transformation and conservation",
      domain: "SCIENCE",
      difficulty: "INTERMEDIATE",
      estimatedTimeMinutes: 55,
      tags: ["physics", "energy", "conservation"],
      status: "PUBLISHED",
    },
    {
      id: "module-math-001",
      slug: "calculus-basics",
      title: "Calculus Fundamentals",
      description: "Introduction to differential calculus",
      domain: "MATH",
      difficulty: "INTERMEDIATE",
      estimatedTimeMinutes: 90,
      tags: ["math", "calculus", "derivatives"],
      status: "PUBLISHED",
    },
    {
      id: "module-math-002",
      slug: "linear-algebra",
      title: "Linear Algebra Essentials",
      description: "Vectors, matrices, and linear transformations",
      domain: "MATH",
      difficulty: "ADVANCED",
      estimatedTimeMinutes: 120,
      tags: ["math", "linear-algebra", "matrices"],
      status: "PUBLISHED",
    },
    {
      id: "module-cs-001",
      slug: "intro-programming",
      title: "Introduction to Programming",
      description: "Learn programming fundamentals with Python",
      domain: "TECH",
      difficulty: "INTRO",
      estimatedTimeMinutes: 60,
      tags: ["programming", "python", "basics"],
      status: "PUBLISHED",
    },
    {
      id: "module-cs-002",
      slug: "data-structures",
      title: "Data Structures",
      description: "Arrays, linked lists, trees, and graphs",
      domain: "TECH",
      difficulty: "INTERMEDIATE",
      estimatedTimeMinutes: 90,
      tags: ["programming", "data-structures", "algorithms"],
      status: "PUBLISHED",
    },
    {
      id: "module-cs-003",
      slug: "algorithms",
      title: "Algorithm Design",
      description: "Sorting, searching, and optimization algorithms",
      domain: "TECH",
      difficulty: "ADVANCED",
      estimatedTimeMinutes: 120,
      tags: ["programming", "algorithms", "optimization"],
      status: "PUBLISHED",
    },
    // Draft modules
    {
      id: "module-draft-001",
      slug: "quantum-mechanics",
      title: "Quantum Mechanics Introduction",
      description: "Basic concepts of quantum physics",
      domain: "SCIENCE",
      difficulty: "ADVANCED",
      estimatedTimeMinutes: 150,
      tags: ["physics", "quantum", "advanced"],
      status: "DRAFT",
    },
    {
      id: "module-draft-002",
      slug: "machine-learning",
      title: "Machine Learning Basics",
      description: "Introduction to ML concepts",
      domain: "TECH",
      difficulty: "ADVANCED",
      estimatedTimeMinutes: 180,
      tags: ["ml", "ai", "data-science"],
      status: "DRAFT",
    },
  ];

  for (const module of modules) {
    const { tags, ...moduleData } = module;
    await prisma.module.upsert({
      where: { id: module.id },
      update: {},
      create: {
        id: moduleData.id,
        tenantId: TENANT_ID,
        slug: moduleData.slug,
        title: moduleData.title,
        description: moduleData.description,
        domain: moduleData.domain as any,
        difficulty: moduleData.difficulty as any,
        estimatedTimeMinutes: moduleData.estimatedTimeMinutes,
        status: moduleData.status as any,
        version: 1,
        publishedAt: moduleData.status === "PUBLISHED" ? new Date() : null,
      },
    });

    // Add tags separately
    for (const tag of tags) {
      try {
        await prisma.moduleTag.create({
          data: {
            moduleId: module.id,
            label: tag,
          },
        });
      } catch {
        // Tag may already exist
      }
    }
  }

  console.log(`✅ ${modules.length} modules seeded`);
}

async function seedClassrooms() {
  console.log("🏫 Seeding classrooms...");

  const classrooms = [
    {
      id: "classroom-physics-101",
      name: "Physics 101 - Fall 2024",
      description: "Introductory physics course",
      teacherId: "user-teacher-002",
    },
    {
      id: "classroom-physics-201",
      name: "Physics 201 - Advanced Mechanics",
      description: "Advanced mechanics for physics majors",
      teacherId: "user-teacher-002",
    },
    {
      id: "classroom-math-101",
      name: "Mathematics 101",
      description: "Foundational mathematics",
      teacherId: "user-teacher-003",
    },
    {
      id: "classroom-cs-101",
      name: "Computer Science 101",
      description: "Introduction to programming",
      teacherId: TEACHER_USER_ID,
    },
  ];

  for (const classroom of classrooms) {
    await prisma.classroom.upsert({
      where: { id: classroom.id },
      update: {},
      create: {
        id: classroom.id,
        tenantId: TENANT_ID,
        teacherId: classroom.teacherId,
        name: classroom.name,
        description: classroom.description,
      },
    });

    // Add students to classrooms using ClassroomStudent model
    const studentIds = Array.from({ length: 15 }, (_, i) =>
      `user-student-${String(i + 1 + (classrooms.indexOf(classroom) * 10)).padStart(3, "0")}`
    ).filter((_, i) => i < 50); // Ensure we don't exceed student count

    for (const studentId of studentIds) {
      try {
        await prisma.classroomStudent.upsert({
          where: {
            classroomId_userId: {
              classroomId: classroom.id,
              userId: studentId,
            },
          },
          update: {},
          create: {
            classroomId: classroom.id,
            userId: studentId,
            displayName: `Student ${studentId.split("-").pop()}`,
          },
        });
      } catch (e) {
        // Student may not exist
      }
    }
  }

  console.log(`✅ ${classrooms.length} classrooms seeded`);
}

async function seedEnrollments() {
  console.log("📝 Seeding enrollments...");

  const moduleIds = [
    "module-physics-001",
    "module-physics-002",
    "module-math-001",
    "module-cs-001",
    "module-cs-002",
  ];

  let enrollmentCount = 0;

  for (let i = 1; i <= 50; i++) {
    const studentId = `user-student-${String(i).padStart(3, "0")}`;

    // Each student enrolls in 2-4 random modules
    const numEnrollments = 2 + Math.floor(Math.random() * 3);
    const selectedModules = moduleIds
      .sort(() => Math.random() - 0.5)
      .slice(0, numEnrollments);

    for (const moduleId of selectedModules) {
      const status = Math.random() > 0.3
        ? (Math.random() > 0.5 ? "COMPLETED" : "IN_PROGRESS")
        : "NOT_STARTED";

      const progress = status === "COMPLETED"
        ? 100
        : status === "IN_PROGRESS"
          ? Math.floor(Math.random() * 80) + 10
          : 0;

      const startedAt = status !== "NOT_STARTED"
        ? new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000)
        : null;

      await prisma.enrollment.upsert({
        where: {
          tenantId_userId_moduleId: {
            tenantId: TENANT_ID,
            userId: studentId,
            moduleId,
          },
        },
        update: {},
        create: {
          tenantId: TENANT_ID,
          userId: studentId,
          moduleId,
          status: status as any,
          progressPercent: progress,
          startedAt,
          completedAt: status === "COMPLETED" ? new Date() : null,
          timeSpentSeconds: Math.floor(Math.random() * 3600) + 600,
        },
      });

      enrollmentCount++;
    }
  }

  console.log(`✅ ${enrollmentCount} enrollments seeded`);
}

async function seedLearningEvents() {
  console.log("📊 Seeding learning events...");

  const eventTypes = [
    "module_viewed",
    "module_completed",
    "assessment_started",
    "assessment_completed",
    "ai_tutor_message",
  ];

  const moduleIds = [
    "module-physics-001",
    "module-physics-002",
    "module-math-001",
    "module-cs-001",
  ];

  let eventCount = 0;

  // Generate events for the last 30 days
  for (let day = 0; day < 30; day++) {
    const date = new Date(Date.now() - day * 24 * 60 * 60 * 1000);

    // 20-50 events per day
    const eventsPerDay = 20 + Math.floor(Math.random() * 30);

    for (let e = 0; e < eventsPerDay; e++) {
      const studentNum = Math.floor(Math.random() * 50) + 1;
      const studentId = `user-student-${String(studentNum).padStart(3, "0")}`;
      const moduleId = moduleIds[Math.floor(Math.random() * moduleIds.length)];
      const eventType = eventTypes[Math.floor(Math.random() * eventTypes.length)];

      await prisma.learningEvent.create({
        data: {
          tenantId: TENANT_ID,
          userId: studentId,
          moduleId,
          eventType,
          timestamp: new Date(date.getTime() + Math.random() * 24 * 60 * 60 * 1000),
          payload: {},
        },
      });

      eventCount++;
    }
  }

  console.log(`✅ ${eventCount} learning events seeded`);
}

async function seedSsoProviders() {
  console.log("🔐 Seeding SSO providers...");

  const providers = [
    {
      id: "sso-okta-001",
      type: "oidc",
      displayName: "Okta Enterprise",
      discoveryEndpoint: "https://demo.okta.com/.well-known/openid-configuration",
      clientId: "demo-client-id",
      clientSecret: "demo-client-secret",
      allowedDomains: ["demo.tutorputor.com", "demo.edu"],
      enabled: true,
      status: "active",
    },
    {
      id: "sso-azure-001",
      type: "oidc",
      displayName: "Azure AD",
      discoveryEndpoint: "https://login.microsoftonline.com/demo/v2.0/.well-known/openid-configuration",
      clientId: "azure-client-id",
      clientSecret: "azure-client-secret",
      allowedDomains: ["demo.onmicrosoft.com"],
      enabled: true,
      status: "active",
    },
    {
      id: "sso-google-001",
      type: "oidc",
      displayName: "Google Workspace",
      discoveryEndpoint: "https://accounts.google.com/.well-known/openid-configuration",
      clientId: "google-client-id",
      clientSecret: "google-client-secret",
      allowedDomains: ["demo.edu"],
      enabled: false,
      status: "pending_verification",
    },
  ];

  for (const provider of providers) {
    await prisma.identityProvider.upsert({
      where: { id: provider.id },
      update: {},
      create: {
        id: provider.id,
        tenantId: TENANT_ID,
        type: provider.type,
        displayName: provider.displayName,
        discoveryEndpoint: provider.discoveryEndpoint,
        clientId: provider.clientId,
        clientSecret: provider.clientSecret,
        allowedDomains: JSON.stringify(provider.allowedDomains),
        enabled: provider.enabled,
        status: provider.status,
        lastSuccessfulAuthAt: provider.status === "active" ? new Date() : null,
      },
    });
  }

  console.log(`✅ ${providers.length} SSO providers seeded`);
}

async function seedComplianceRequests() {
  console.log("📋 Seeding compliance requests...");

  // Data export requests
  const exportRequests = [
    {
      id: "export-001",
      userId: "user-student-001",
      status: "completed",
      requestedAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
      completedAt: new Date(Date.now() - 6 * 24 * 60 * 60 * 1000),
      downloadUrl: "https://exports.tutorputor.com/export-001.zip",
    },
    {
      id: "export-002",
      userId: "user-student-010",
      status: "pending",
      requestedAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000),
    },
    {
      id: "export-003",
      userId: "user-student-020",
      status: "processing",
      requestedAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000),
    },
  ];

  for (const request of exportRequests) {
    await prisma.dataExportRequest.upsert({
      where: { id: request.id },
      update: {},
      create: {
        ...request,
        tenantId: TENANT_ID,
        estimatedCompletionAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
      },
    });
  }

  // Data deletion requests
  const deletionRequests = [
    {
      id: "deletion-001",
      userId: "user-student-045",
      status: "scheduled",
      requestedAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
      scheduledDeletionAt: new Date(Date.now() + 27 * 24 * 60 * 60 * 1000),
      retentionDays: 30,
    },
    {
      id: "deletion-002",
      userId: "user-student-046",
      status: "completed",
      requestedAt: new Date(Date.now() - 35 * 24 * 60 * 60 * 1000),
      scheduledDeletionAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
      completedAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000),
      retentionDays: 30,
    },
  ];

  for (const request of deletionRequests) {
    await prisma.dataDeletionRequest.upsert({
      where: { id: request.id },
      update: {},
      create: {
        ...request,
        tenantId: TENANT_ID,
      },
    });
  }

  console.log(`✅ ${exportRequests.length + deletionRequests.length} compliance requests seeded`);
}

async function seedAuditLogs() {
  console.log("📜 Seeding audit logs...");

  const actions = [
    { action: "user_login", resourceType: "user" },
    { action: "role_assigned", resourceType: "user" },
    { action: "sso_config_created", resourceType: "sso_config" },
    { action: "sso_config_updated", resourceType: "sso_config" },
    { action: "tenant_settings_updated", resourceType: "tenant" },
    { action: "data_export_requested", resourceType: "user" },
  ];

  let logCount = 0;

  // Generate audit logs for the last 14 days
  for (let day = 0; day < 14; day++) {
    const date = new Date(Date.now() - day * 24 * 60 * 60 * 1000);

    // 5-15 audit events per day
    const eventsPerDay = 5 + Math.floor(Math.random() * 10);

    for (let e = 0; e < eventsPerDay; e++) {
      const actionInfo = actions[Math.floor(Math.random() * actions.length)];
      const actorId = Math.random() > 0.7 ? ADMIN_USER_ID : TEACHER_USER_ID;

      await prisma.auditLog.create({
        data: {
          tenantId: TENANT_ID,
          actorId,
          action: actionInfo.action,
          resourceType: actionInfo.resourceType,
          resourceId: `resource-${Math.floor(Math.random() * 100)}`,
          outcome: Math.random() > 0.1 ? "success" : "failure",
          timestamp: new Date(date.getTime() + Math.random() * 24 * 60 * 60 * 1000),
          ipAddress: `192.168.1.${Math.floor(Math.random() * 255)}`,
          userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          metadata: "{}",
        },
      });

      logCount++;
    }
  }

  console.log(`✅ ${logCount} audit logs seeded`);
}

async function seedAssessments() {
  console.log("📝 Seeding assessments...");

  const assessments = [
    {
      id: "assessment-physics-001",
      moduleId: "module-physics-001",
      title: "Mechanics Quiz",
      type: "QUIZ",
      status: "PUBLISHED",
      passingScore: 70,
      attemptsAllowed: 3,
      timeLimitMinutes: 30,
    },
    {
      id: "assessment-physics-002",
      moduleId: "module-physics-002",
      title: "Newton's Laws Test",
      type: "QUIZ",
      status: "PUBLISHED",
      passingScore: 75,
      attemptsAllowed: 2,
      timeLimitMinutes: 45,
    },
    {
      id: "assessment-math-001",
      moduleId: "module-math-001",
      title: "Calculus Fundamentals Exam",
      type: "QUIZ",
      status: "PUBLISHED",
      passingScore: 80,
      attemptsAllowed: 2,
      timeLimitMinutes: 60,
    },
  ];

  for (const assessment of assessments) {
    await prisma.assessment.upsert({
      where: { id: assessment.id },
      update: {},
      create: {
        id: assessment.id,
        tenantId: TENANT_ID,
        moduleId: assessment.moduleId,
        title: assessment.title,
        type: assessment.type as any,
        status: assessment.status as any,
        passingScore: assessment.passingScore,
        attemptsAllowed: assessment.attemptsAllowed,
        timeLimitMinutes: assessment.timeLimitMinutes,
        createdBy: TEACHER_USER_ID,
        updatedBy: TEACHER_USER_ID,
        version: 1,
      },
    });
  }

  // Seed assessment attempts
  let attemptCount = 0;
  for (const assessment of assessments) {
    for (let i = 1; i <= 20; i++) {
      const studentId = `user-student-${String(i).padStart(3, "0")}`;
      const score = Math.floor(Math.random() * 40) + 60; // 60-100

      await prisma.assessmentAttempt.create({
        data: {
          tenantId: TENANT_ID,
          assessmentId: assessment.id,
          userId: studentId,
          status: "GRADED",
          responses: {},
          scorePercent: score,
          startedAt: new Date(Date.now() - Math.random() * 14 * 24 * 60 * 60 * 1000),
          submittedAt: new Date(Date.now() - Math.random() * 14 * 24 * 60 * 60 * 1000),
          gradedAt: new Date(),
          timeSpentSeconds: Math.floor(Math.random() * 1800) + 600,
        },
      });

      attemptCount++;
    }
  }

  console.log(`✅ ${assessments.length} assessments and ${attemptCount} attempts seeded`);
}

// =============================================================================
// Content Authoring Seed (Domains, Concepts, Simulations, Visualizations, Examples)
// =============================================================================

async function seedContentAuthoring() {
  console.log("📚 Seeding content authoring data...");

  // Delete existing content for idempotency (keeping domains for now)
  const existingDomains = await prisma.domainAuthor.findMany({
    where: { tenantId: TENANT_ID },
  });

  for (const domain of existingDomains) {
    await prisma.contentExample.deleteMany({
      where: {
        concept: {
          domainId: domain.id,
        },
      },
    });

    await prisma.simulationDefinition.deleteMany({
      where: {
        concept: {
          domainId: domain.id,
        },
      },
    });

    await prisma.visualizationDefinition.deleteMany({
      where: {
        concept: {
          domainId: domain.id,
        },
      },
    });

    await prisma.domainAuthorConcept.deleteMany({
      where: { domainId: domain.id },
    });
  }

  // Create Physics Domain
  const physicsDomain = await prisma.domainAuthor.upsert({
    where: {
      tenantId_domain: {
        tenantId: TENANT_ID,
        domain: "PHYSICS",
      },
    },
    update: {},
    create: {
      tenantId: TENANT_ID,
      domain: "PHYSICS",
      title: "Physics Fundamentals",
      description:
        "Comprehensive physics curriculum covering mechanics, thermodynamics, and wave motion",
      author: CREATOR_USER_ID,
      status: "PUBLISHED",
    },
  });

  // Create Chemistry Domain
  const chemistryDomain = await prisma.domainAuthor.upsert({
    where: {
      tenantId_domain: {
        tenantId: TENANT_ID,
        domain: "CHEMISTRY",
      },
    },
    update: {},
    create: {
      tenantId: TENANT_ID,
      domain: "CHEMISTRY",
      title: "Chemistry Essentials",
      description:
        "Foundation chemistry course covering atomic structure, bonding, and reactions",
      author: CREATOR_USER_ID,
      status: "PUBLISHED",
    },
  });

  // Create Biology Domain
  const biologyDomain = await prisma.domainAuthor.upsert({
    where: {
      tenantId_domain: {
        tenantId: TENANT_ID,
        domain: "BIOLOGY",
      },
    },
    update: {},
    create: {
      tenantId: TENANT_ID,
      domain: "BIOLOGY",
      title: "Biology & Life Sciences",
      description:
        "Biological sciences curriculum covering cells, genetics, and evolution",
      author: CREATOR_USER_ID,
      status: "PUBLISHED",
    },
  });

  // Physics Concepts
  const newtonLaw = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: physicsDomain.id,
        name: "Newton's Laws of Motion",
      },
    },
    update: {},
    create: {
      domainId: physicsDomain.id,
      name: "Newton's Laws of Motion",
      description:
        "Three fundamental laws describing the motion of objects and forces",
      keywords: JSON.stringify(["force", "acceleration", "mass", "motion"]),
      level: "INTERMEDIATE",
      learningObjectives: JSON.stringify([
        "Understand Newton's three laws",
        "Apply laws to solve motion problems",
        "Relate forces to acceleration",
      ]),
      prerequisites: JSON.stringify([]),
      competencies: JSON.stringify(["physics", "mechanics"]),
    },
  });

  const energy = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: physicsDomain.id,
        name: "Energy Conservation",
      },
    },
    update: {},
    create: {
      domainId: physicsDomain.id,
      name: "Energy Conservation",
      description:
        "Energy cannot be created or destroyed, only transformed between forms",
      keywords: JSON.stringify(["kinetic", "potential", "conservation", "transformation"]),
      level: "INTERMEDIATE",
      learningObjectives: JSON.stringify([
        "Understand energy conservation principle",
        "Identify energy transformations",
        "Calculate energy in systems",
      ]),
      prerequisites: JSON.stringify([newtonLaw.id]),
      competencies: JSON.stringify(["physics", "energy"]),
    },
  });

  const waves = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: physicsDomain.id,
        name: "Wave Motion",
      },
    },
    update: {},
    create: {
      domainId: physicsDomain.id,
      name: "Wave Motion",
      description:
        "Periodic disturbances that propagate through space and time",
      keywords: JSON.stringify(["frequency", "wavelength", "amplitude", "propagation"]),
      level: "ADVANCED",
      learningObjectives: JSON.stringify([
        "Describe wave characteristics",
        "Apply wave equations",
        "Understand interference patterns",
      ]),
      prerequisites: JSON.stringify([]),
      competencies: JSON.stringify(["physics", "waves"]),
    },
  });

  // Chemistry Concepts
  const bonding = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: chemistryDomain.id,
        name: "Chemical Bonding",
      },
    },
    update: {},
    create: {
      domainId: chemistryDomain.id,
      name: "Chemical Bonding",
      description:
        "Forces holding atoms together in molecules and compounds",
      keywords: JSON.stringify(["ionic", "covalent", "metallic", "electronegativity"]),
      level: "INTERMEDIATE",
      learningObjectives: JSON.stringify([
        "Distinguish bond types",
        "Predict bonding behavior",
        "Understand bond strength",
      ]),
      prerequisites: JSON.stringify([]),
      competencies: JSON.stringify(["chemistry", "bonding"]),
    },
  });

  const reactions = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: chemistryDomain.id,
        name: "Chemical Reactions",
      },
    },
    update: {},
    create: {
      domainId: chemistryDomain.id,
      name: "Chemical Reactions",
      description:
        "Processes where substances are transformed into different substances",
      keywords: JSON.stringify(["reactants", "products", "equilibrium", "rates"]),
      level: "INTERMEDIATE",
      learningObjectives: JSON.stringify([
        "Write balanced equations",
        "Predict reaction products",
        "Understand reaction rates",
      ]),
      prerequisites: JSON.stringify([bonding.id]),
      competencies: JSON.stringify(["chemistry", "reactions"]),
    },
  });

  const periodicity = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: chemistryDomain.id,
        name: "Periodic Trends",
      },
    },
    update: {},
    create: {
      domainId: chemistryDomain.id,
      name: "Periodic Trends",
      description:
        "Regular patterns in element properties across the periodic table",
      keywords: JSON.stringify(["ionization", "electronegativity", "atomic radius"]),
      level: "FOUNDATIONAL",
      learningObjectives: JSON.stringify([
        "Identify periodic trends",
        "Predict element properties",
        "Understand periodic organization",
      ]),
      prerequisites: JSON.stringify([]),
      competencies: JSON.stringify(["chemistry", "periodic"]),
    },
  });

  // Biology Concepts
  const cellBiology = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: biologyDomain.id,
        name: "Cell Structure and Function",
      },
    },
    update: {},
    create: {
      domainId: biologyDomain.id,
      name: "Cell Structure and Function",
      description:
        "Organization and roles of cellular components in living organisms",
      keywords: JSON.stringify(["organelle", "nucleus", "membrane", "transport"]),
      level: "FOUNDATIONAL",
      learningObjectives: JSON.stringify([
        "Identify cell organelles",
        "Describe cell functions",
        "Understand cell transport",
      ]),
      prerequisites: JSON.stringify([]),
      competencies: JSON.stringify(["biology", "cells"]),
    },
  });

  const genetics = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: biologyDomain.id,
        name: "Genetics and Heredity",
      },
    },
    update: {},
    create: {
      domainId: biologyDomain.id,
      name: "Genetics and Heredity",
      description:
        "Study of inheritance patterns and genetic material transmission",
      keywords: JSON.stringify(["DNA", "mutation", "inheritance", "dominant", "recessive"]),
      level: "INTERMEDIATE",
      learningObjectives: JSON.stringify([
        "Understand inheritance patterns",
        "Analyze genetic crosses",
        "Explain mutations",
      ]),
      prerequisites: JSON.stringify([cellBiology.id]),
      competencies: JSON.stringify(["biology", "genetics"]),
    },
  });

  const evolution = await prisma.domainAuthorConcept.upsert({
    where: {
      domainId_name: {
        domainId: biologyDomain.id,
        name: "Evolution and Natural Selection",
      },
    },
    update: {},
    create: {
      domainId: biologyDomain.id,
      name: "Evolution and Natural Selection",
      description:
        "Process of adaptation and change in populations over time",
      keywords: JSON.stringify(["adaptation", "selection", "variation", "speciation"]),
      level: "ADVANCED",
      learningObjectives: JSON.stringify([
        "Understand natural selection",
        "Explain evolutionary adaptation",
        "Analyze evolutionary evidence",
      ]),
      prerequisites: JSON.stringify([genetics.id]),
      competencies: JSON.stringify(["biology", "evolution"]),
    },
  });

  // Create examples for each concept (3 examples per concept)
  const concepts = [
    { concept: newtonLaw, name: "Newton's First Law" },
    { concept: energy, name: "Kinetic Energy" },
    { concept: waves, name: "Electromagnetic Waves" },
    { concept: bonding, name: "Ionic Bonding" },
    { concept: reactions, name: "Combustion Reaction" },
    { concept: periodicity, name: "Atomic Radius Trends" },
    { concept: cellBiology, name: "Mitochondrial Function" },
    { concept: genetics, name: "Mendel's Laws" },
    { concept: evolution, name: "Darwin's Finches" },
  ];

  for (const { concept, name } of concepts) {
    // Example 1: Textbook explanation
    await prisma.contentExample.create({
      data: {
        conceptId: concept.id,
        title: `${name} - Textbook Definition`,
        description: `${name} is a fundamental concept in scientific understanding. This type of example provides a clear, academic definition suitable for classroom reference and study materials.`,
        problemStatement: `Define and explain ${name} in detail`,
        solutionContent: `${name} is a key concept. It involves fundamental principles that students must understand for success in this subject.`,
        keyLearningPoints: JSON.stringify([
          "Key definition and terminology",
          "Core principles involved",
          "Significance in the field",
        ]),
        difficulty: "FOUNDATIONAL",
      },
    });

    // Example 2: Problem-solving
    await prisma.contentExample.create({
      data: {
        conceptId: concept.id,
        title: `${name} - Practice Problem`,
        description: `Sample Problem: Apply ${name} to solve the following scenario`,
        problemStatement: `A student encounters a practical problem involving ${name}. They need to: 1) Identify the key aspects, 2) Apply relevant principles, 3) Solve step by step`,
        solutionContent: `Step 1: Understand ${name}
Step 2: Apply the relevant equations or principles
Step 3: Verify the solution against known standards
Step 4: Reflect on the problem-solving approach`,
        keyLearningPoints: JSON.stringify([
          "Problem analysis technique",
          "Step-by-step solution process",
          "Validation and verification",
        ]),
        difficulty: "INTERMEDIATE",
      },
    });

    // Example 3: Real-world application
    await prisma.contentExample.create({
      data: {
        conceptId: concept.id,
        title: `${name} - Real World Application`,
        description: `In real-world applications, ${name} manifests in many everyday scenarios`,
        problemStatement: `How does ${name} apply to real-world engineering and science?`,
        solutionContent: `In practical applications, ${name} is used in multiple industries:
- Engineers apply this principle in designing structures and systems
- Scientists use it to understand natural phenomena
- Medical professionals employ these concepts in healthcare
- Technologists build technologies based on these principles`,
        keyLearningPoints: JSON.stringify([
          "Real-world applications",
          "Industrial implementation",
          "Practical problem solving",
        ]),
        difficulty: "ADVANCED",
      },
    });
  }

  // Create simulations for a few concepts
  await prisma.simulationDefinition.create({
    data: {
      conceptId: newtonLaw.id,
      type: "physics-2D",
      purpose: "Interactive demonstration of Newton's Laws with real-time visualization",
      manifest: {
        title: "Newton's Laws Interactive Simulator",
        description: "Interactive simulation demonstrating forces, acceleration, and motion",
        parameters: {
          gravity: 9.81,
          maxForce: 100,
          initialVelocity: 0,
          friction: 0.1,
        },
        scenes: [
          {
            title: "Force and Acceleration",
            description: "Explore how force affects acceleration",
          },
          {
            title: "Friction Effects",
            description: "Understand the role of friction in motion",
          },
        ],
      },
      interactivityLevel: "high",
    },
  });

  await prisma.simulationDefinition.create({
    data: {
      conceptId: waves.id,
      type: "interactive_visualization",
      purpose: "Visual demonstration of wave interference patterns and phenomena",
      manifest: {
        title: "Wave Interference Simulator",
        description: "Demonstrates constructive and destructive interference patterns",
        parameters: {
          frequency1: 1,
          frequency2: 1.2,
          amplitude: 1,
          wavelength: 10,
        },
        visualizations: [
          {
            type: "sine_waves",
            title: "Individual Waves",
          },
          {
            type: "interference_pattern",
            title: "Resultant Pattern",
          },
        ],
      },
      interactivityLevel: "high",
    },
  });

  await prisma.simulationDefinition.create({
    data: {
      conceptId: reactions.id,
      type: "molecular_interaction",
      purpose: "Visualize molecular transformations during chemical reactions",
      manifest: {
        title: "Chemical Reaction Visualizer",
        description: "Visualizes molecular interactions during chemical reactions",
        parameters: {
          temperature: 298,
          pressure: 1,
          concentration: 1,
          reactionRate: 1,
        },
        reactions: [
          {
            name: "Combustion",
            equation: "CH4 + 2O2 → CO2 + 2H2O",
          },
          {
            name: "Acid-Base",
            equation: "HCl + NaOH → NaCl + H2O",
          },
        ],
      },
      interactivityLevel: "medium",
    },
  });

  // Create visualizations for concepts
  await prisma.visualizationDefinition.create({
    data: {
      conceptId: cellBiology.id,
      type: "3d_model",
      config: {
        modelType: "cell",
        scale: 1000,
        showLabels: true,
        rotatable: true,
        organelles: [
          {
            name: "Nucleus",
            color: "#FF6B6B",
            position: { x: 0, y: 0, z: 0 },
          },
          {
            name: "Mitochondria",
            color: "#4ECDC4",
            position: { x: 200, y: 100, z: 0 },
          },
          {
            name: "Ribosome",
            color: "#FFE66D",
            position: { x: -150, y: 150, z: 0 },
          },
        ],
      },
    },
  });

  await prisma.visualizationDefinition.create({
    data: {
      conceptId: genetics.id,
      type: "diagram",
      config: {
        diagramType: "dna_double_helix",
        showBasepairing: true,
        showBackbone: true,
        animateRotation: true,
        basePairs: [
          { base1: "A", base2: "T" },
          { base1: "G", base2: "C" },
          { base1: "A", base2: "T" },
          { base1: "T", base2: "A" },
        ],
        colorScheme: {
          adenine: "#FF6B6B",
          thymine: "#4ECDC4",
          guanine: "#FFE66D",
          cytosine: "#95E1D3",
        },
      },
    },
  });

  await prisma.visualizationDefinition.create({
    data: {
      conceptId: evolution.id,
      type: "animation",
      config: {
        animationType: "timeline",
        startYear: -4000000000,
        endYear: 0,
        speed: 1,
        showMilestones: true,
        milestones: [
          {
            year: -3500000000,
            event: "Single-celled organisms",
          },
          {
            year: -500000000,
            event: "Multicellular life",
          },
          {
            year: -65000000,
            event: "Dinosaur extinction",
          },
          {
            year: -6000000,
            event: "Human-ape divergence",
          },
          {
            year: -200000,
            event: "Modern humans emerge",
          },
        ],
      },
    },
  });

  console.log(
    `✅ Content authoring data seeded: 3 domains, 9 concepts, 27 examples, 3 simulations, 3 visualizations`
  );
}

// =============================================================================
// Main
// =============================================================================

export async function seedAdminDemoData() {
  console.log("🌱 Starting TutorPutor Admin seed...\n");

  try {
    prisma = createPrismaClient();
    await seedTenant();
    await seedUsers();
    await seedModules();
    await seedClassrooms();
    await seedEnrollments();
    await seedLearningEvents();
    await seedSsoProviders();
    await seedComplianceRequests();
    await seedAuditLogs();
    await seedAssessments();
    await seedContentAuthoring();
    await seedLearningUnits(prisma, { tenantId: TENANT_ID, createdBy: CREATOR_USER_ID });
    await seedSimulations(prisma, { tenantId: TENANT_ID });

    // Seed content studio experiences
    const { seedContentStudioData } = await import("./seed-experiences.js");
    await seedContentStudioData();

    console.log("\n✅ All seed data created successfully!");
    console.log("\n📊 Summary:");
    console.log("  - 1 tenant with settings");
    console.log("  - 58 users (2 admins, 3 teachers, 2 creators, 1 demo instructor, 50 students)");
    console.log("  - 10 modules (8 published, 2 draft)");
    console.log("  - 4 classrooms with members");
    console.log("  - 100+ enrollments");
    console.log("  - 600+ learning events");
    console.log("  - 3 SSO providers");
    console.log("  - 5 compliance requests");
    console.log("  - 100+ audit logs");
    console.log("  - 3 assessments with 60 attempts");
    console.log("  - 3 domains with 9 concepts, 27 examples, 3 simulations, 3 visualizations");
    console.log("  - 3 content studio experiences with interactive content");
  } catch (error) {
    console.error("❌ Seed failed:", error);
    throw error;
  } finally {
    await prisma?.$disconnect?.();
  }
}
