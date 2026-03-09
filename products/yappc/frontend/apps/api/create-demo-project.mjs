#!/usr/bin/env node

import pkg from '@prisma/client';
const { PrismaClient } = pkg;

const prisma = new PrismaClient();

async function main() {
    try {
        // Get the default workspace or first workspace
        const workspace = await prisma.workspace.findFirst({
            where: {
                isDefault: true,
            },
        });

        if (!workspace) {
            console.log('❌ No default workspace found');
            process.exit(1);
        }

        console.log(`✅ Found workspace: ${workspace.name} (${workspace.id})`);

        // Create a demo project
        const project = await prisma.project.create({
            data: {
                name: 'Canvas Demo Project',
                description: 'A demo project to test the Canvas-First UX with lifecycle phases',
                type: 'FULL_STACK',
                status: 'ACTIVE',
                lifecyclePhase: 'SHAPE',
                ownerWorkspaceId: workspace.id,
                createdById: workspace.ownerId,
                isDefault: true,
                aiNextActions: [
                    'Design user interface wireframes',
                    'Define API endpoints',
                    'Create test scenarios',
                ],
                aiHealthScore: 65,
                aiSummary: 'Early stage project with basic structure defined',
            },
        });

        console.log(`✅ Created project: ${project.name} (${project.id})`);
        console.log(`📍 Access at: http://localhost:7002/journey`);
        console.log(`🎯 Then navigate to the Canvas from the project`);
    } catch (error) {
        console.error('❌ Error:', error.message);
        process.exit(1);
    } finally {
        await prisma.$disconnect();
    }
}

main();
