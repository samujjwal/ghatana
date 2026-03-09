/**
 * Seed script for Content Studio Experiences
 * 
 * Creates sample learning experiences for content studio testing
 * 
 * @doc.type script
 * @doc.purpose Seed experiences for content studio
 * @doc.layer product
 * @doc.pattern Script
 */

import { createPrismaClient, DEFAULT_TENANT_ID } from "../src/index.js";

let prisma: any;

const TENANT_ID = process.env.TUTORPUTOR_DEFAULT_TENANT_ID ?? DEFAULT_TENANT_ID;
const CREATOR_USER_ID = "user-creator-001";

async function seedExperiences() {
    console.log("📚 Seeding content studio experiences...");

    const experiences = [
        {
            id: "exp_demo_1",
            title: "Projectile Motion Fundamentals",
            domain: "SCIENCE" as any,
            intentProblem: "Students need to understand projectile motion principles through hands-on exploration",
            intentMotivation: "Physics is best learned through interactive visualization and experimentation",
            intentMisconceptions: JSON.stringify([
                "Heavier objects fall faster",
                "Launch angle doesn't affect range",
                "Higher velocity always means longer flight time"
            ]),
            targetGrades: JSON.stringify([
                { min: 9, max: 12, description: "High School" }
            ]),
            curriculumAlignment: JSON.stringify([]),
            gradeAdaptations: JSON.stringify([]),
            assessmentConfig: JSON.stringify({
                type: "formative",
                questions: [
                    {
                        id: "q1",
                        type: "multiple_choice",
                        question: "What angle gives the maximum projectile range in ideal conditions?",
                        options: ["30°", "45°", "60°", "90°"],
                        correct: 1,
                        feedback: {
                            correct: "Correct! 45° maximizes sin(2θ) = 1",
                            incorrect: "Review the range equation R = v²sin(2θ)/g"
                        }
                    }
                ]
            }),
            status: "PUBLISHED" as any,
            version: 1,
            estimatedTimeMinutes: 45,
            createdBy: CREATOR_USER_ID,
            publishedAt: new Date(),
            promptHash: "demo_hash_1",
            riskLevel: "LOW" as any
        },
        {
            id: "exp_demo_2",
            title: "Chemical Bonding Basics",
            domain: "SCIENCE" as any,
            intentProblem: "Students struggle to visualize abstract chemical bonding concepts",
            intentMotivation: "Chemistry becomes intuitive when students can see bonds forming",
            intentMisconceptions: JSON.stringify([
                "Atoms are solid spheres",
                "Electrons orbit like planets",
                "All bonds are the same"
            ]),
            targetGrades: JSON.stringify([
                { min: 9, max: 12, description: "High School" }
            ]),
            curriculumAlignment: JSON.stringify([]),
            gradeAdaptations: JSON.stringify([]),
            assessmentConfig: JSON.stringify({
                type: "formative",
                questions: [
                    {
                        id: "q2",
                        type: "multiple_choice",
                        question: "What type of bond forms between sodium and chlorine?",
                        options: ["Ionic", "Covalent", "Metallic", "Hydrogen"],
                        correct: 0
                    }
                ]
            }),
            status: "DRAFT" as any,
            version: 1,
            estimatedTimeMinutes: 40,
            createdBy: CREATOR_USER_ID,
            promptHash: "demo_hash_2",
            riskLevel: "LOW" as any
        },
        {
            id: "exp_demo_3",
            title: "Newton's Laws of Motion",
            domain: "SCIENCE" as any,
            intentProblem: "Students need concrete examples to understand abstract physics laws",
            intentMotivation: "Newton's laws govern everyday motion and are fundamental to physics",
            intentMisconceptions: JSON.stringify([
                "Force causes motion (not acceleration)",
                "Objects in motion need continuous force",
                "Action and reaction are the same force"
            ]),
            targetGrades: JSON.stringify([
                { min: 9, max: 12, description: "High School" }
            ]),
            curriculumAlignment: JSON.stringify([]),
            gradeAdaptations: JSON.stringify([]),
            assessmentConfig: JSON.stringify({
                type: "formative",
                questions: [
                    {
                        id: "q3",
                        type: "free_response",
                        question: "Explain Newton's third law in your own words and provide a real-world example"
                    }
                ]
            }),
            status: "PUBLISHED" as any,
            version: 1,
            estimatedTimeMinutes: 50,
            createdBy: CREATOR_USER_ID,
            publishedAt: new Date(),
            promptHash: "demo_hash_3",
            riskLevel: "LOW" as any
        }
    ];

    for (const experience of experiences) {
        await prisma.learningExperience.upsert({
            where: { id: experience.id },
            update: {
                title: experience.title,
                domain: experience.domain,
                intentProblem: experience.intentProblem,
                intentMotivation: experience.intentMotivation,
                intentMisconceptions: experience.intentMisconceptions,
                targetGrades: experience.targetGrades,
                curriculumAlignment: experience.curriculumAlignment,
                gradeAdaptations: experience.gradeAdaptations,
                assessmentConfig: experience.assessmentConfig,
                status: experience.status,
                version: experience.version,
                estimatedTimeMinutes: experience.estimatedTimeMinutes,
                lastEditedBy: experience.createdBy,
                publishedAt: experience.publishedAt,
                updatedAt: new Date()
            },
            create: {
                ...experience,
                tenantId: TENANT_ID,
                createdAt: new Date(Date.now() - Math.random() * 7 * 24 * 60 * 60 * 1000), // Random time in last week
                updatedAt: new Date()
            }
        });
    }

    console.log(`✅ ${experiences.length} experiences seeded`);
}

export async function seedContentStudioData() {
    console.log("🌱 Starting Content Studio seed...\n");

    try {
        prisma = createPrismaClient();
        await seedExperiences();

        console.log("\n✅ Content Studio seed data created successfully!");
        console.log("\n📊 Summary:");
        console.log("  - 3 learning experiences with full content");
        console.log("  - Interactive simulations and animations");
        console.log("  - Formative assessments");
        console.log("  - Claims with evidence and examples");
    } catch (error) {
        console.error("❌ Content Studio seed failed:", error);
        throw error;
    } finally {
        await prisma?.$disconnect?.();
    }
}
