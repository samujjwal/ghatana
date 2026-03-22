/**
 * Mock API Server for Lifecycle Hub
 * 
 * Provides mock endpoints for development/testing:
 * - Artifacts CRUD
 * - Evidence management
 * - Gate status
 * - Task recommendations
 * - AI insights
 * - Audit events
 * - Persona derivation
 * 
 * Usage: node mock-api-server.js
 * Server runs on: http://localhost:3000
 */

import express from 'express';
import cors from 'cors';
import { randomUUID } from 'crypto';

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

// ============================================================================
// In-Memory Data Store
// ============================================================================

const db = {
    artifacts: new Map(),
    evidence: new Map(),
    auditEvents: [],
    tasks: new Map(),
    devSecOpsItems: new Map(),
};

// Seed some initial data
const seedData = () => {
    const projectId = 'test-project';
    const projectId2 = 'demo-project'; // Common seeded project

    const seedProjectData = (pid) => {

        // Seed artifacts
        db.artifacts.set(`${pid}-art-1`, {
            id: `${pid}-art-1`,
            projectId: pid,
            title: 'Initial Problem Statement',
            type: 'Idea Brief',
            content: 'We need to build a lifecycle management system...',
            status: 'approved',
            fowStage: 1,
            phase: 0,
            createdAt: new Date(),
            updatedAt: new Date(),
            createdBy: 'user-1',
        });

        // Seed evidence
        db.evidence.set(`${pid}-ev-1`, {
            id: `${pid}-ev-1`,
            projectId: pid,
            artifactId: `${pid}-art-1`,
            type: 'artifact',
            title: 'Initial Problem Statement',
            timestamp: new Date(),
            phase: 0,
            status: 'approved',
        });

        // Seed audit events
        db.auditEvents.push({
            id: `${pid}-audit-1`,
            projectId: pid,
            action: 'ARTIFACT_CREATED',
            userId: 'user-1',
            timestamp: new Date(),
            fowStage: 1,
            phase: 0,
            metadata: { artifactId: `${pid}-art-1` },
        });

        // Seed DevSecOps Items
        db.devSecOpsItems.set(`${pid}-item-1`, { id: '1', projectId: pid, type: 'task', title: 'Implement Authentication', priority: 'high', status: 'in-progress', risk: 20 });
        db.devSecOpsItems.set(`${pid}-item-2`, { id: '2', projectId: pid, type: 'vulnerability', title: 'CVE-2024-1234 Remediation', priority: 'critical', status: 'todo', risk: 90 });
        db.devSecOpsItems.set(`${pid}-item-3`, { id: '3', projectId: pid, type: 'debt', title: 'Refactor Canvas State', priority: 'medium', status: 'backlog', risk: 40 });
        db.devSecOpsItems.set(`${pid}-item-4`, { id: '4', projectId: pid, type: 'bug', title: 'Fix Layout Shift', priority: 'low', status: 'done', risk: 10 });

        // Seed Tasks
        db.tasks.set(`${pid}-task-1`, { id: `${pid}-task-1`, title: 'Define Problem Statement', type: 'document', description: 'Create a clear problem statement', projectId: pid, phase: 0, status: 'pending' });
        db.tasks.set(`${pid}-task-2`, { id: `${pid}-task-2`, title: 'Architecture Diagram', type: 'canvas', description: 'Design system architecture', projectId: pid, phase: 1, status: 'pending' });
    };

    seedProjectData(projectId);
    seedProjectData(projectId2);

    console.log('✅ Seed data loaded: Artifacts, Evidence, Audit, DevSecOps, Tasks');
};

seedData();

// ============================================================================
// API Endpoints
// ============================================================================

// Health check
app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date() });
});

// ============================================================================
// Artifacts
// ============================================================================

app.get('/api/projects/:projectId/artifacts', (req, res) => {
    const { projectId } = req.params;
    const artifacts = Array.from(db.artifacts.values())
        .filter(a => a.projectId === projectId);
    res.json(artifacts);
});

app.get('/api/projects/:projectId/artifacts/:artifactId', (req, res) => {
    const { artifactId } = req.params;
    const artifact = db.artifacts.get(artifactId);
    if (!artifact) return res.status(404).json({ error: 'Artifact not found' });
    res.json(artifact);
});

app.post('/api/projects/:projectId/artifacts', (req, res) => {
    const { projectId } = req.params;
    const artifact = {
        id: randomUUID(),
        projectId,
        ...req.body,
        createdAt: new Date(),
        updatedAt: new Date(),
    };
    db.artifacts.set(artifact.id, artifact);
    res.status(201).json(artifact);
});

app.put('/api/projects/:projectId/artifacts/:artifactId', (req, res) => {
    const { artifactId } = req.params;
    const artifact = db.artifacts.get(artifactId);
    if (!artifact) return res.status(404).json({ error: 'Artifact not found' });

    const updated = { ...artifact, ...req.body, updatedAt: new Date() };
    db.artifacts.set(artifactId, updated);
    res.json(updated);
});

app.delete('/api/projects/:projectId/artifacts/:artifactId', (req, res) => {
    const { artifactId } = req.params;
    if (!db.artifacts.has(artifactId)) {
        return res.status(404).json({ error: 'Artifact not found' });
    }
    db.artifacts.delete(artifactId);
    res.status(204).send();
});

// ============================================================================
// Evidence
// ============================================================================

app.get('/api/projects/:projectId/evidence', (req, res) => {
    const { projectId } = req.params;
    const evidence = Array.from(db.evidence.values())
        .filter(e => e.projectId === projectId);
    res.json(evidence);
});

app.post('/api/projects/:projectId/evidence', (req, res) => {
    const { projectId } = req.params;
    const evidence = {
        id: randomUUID(),
        projectId,
        ...req.body,
        timestamp: new Date(),
    };
    db.evidence.set(evidence.id, evidence);
    res.status(201).json(evidence);
});

// ============================================================================
// Gates
// ============================================================================

app.get('/api/projects/:projectId/gates/:stage', (req, res) => {
    const { projectId, stage } = req.params;

    // Calculate gate readiness based on artifacts
    const artifacts = Array.from(db.artifacts.values())
        .filter(a => a.projectId === projectId && a.fowStage === parseInt(stage));

    const requiredArtifacts = ['Idea Brief', 'Problem Statement', 'Requirements'];
    const completedArtifacts = artifacts.filter(a => a.status === 'approved').length;
    const readiness = Math.min(100, Math.round((completedArtifacts / requiredArtifacts.length) * 100));

    res.json({
        stage: parseInt(stage),
        readiness,
        requiredArtifacts,
        completedArtifacts: artifacts.filter(a => a.status === 'approved').map(a => a.type),
        missingArtifacts: requiredArtifacts.filter(type =>
            !artifacts.some(a => a.type === type && a.status === 'approved')
        ),
    });
});

app.post('/api/projects/:projectId/gates/transition', (req, res) => {
    const { projectId } = req.params;
    const { fromStage, toStage, userId, reason } = req.body;

    // Record audit event
    db.auditEvents.push({
        id: randomUUID(),
        projectId,
        action: 'STAGE_TRANSITIONED',
        userId,
        timestamp: new Date(),
        fowStage: toStage,
        phase: req.body.phase || 0,
        metadata: { fromStage, toStage, reason },
    });

    res.json({ success: true, fromStage, toStage });
});

// ============================================================================
// Tasks
// ============================================================================

app.get('/api/projects/:projectId/tasks/next-best', (req, res) => {
    const { projectId } = req.params;
    const { phase } = req.query;

    // Return mock next best task
    const tasks = {
        0: { id: 'task-intent-1', title: 'Define your problem statement', type: 'document', description: 'Create a clear problem statement' },
        1: { id: 'task-shape-1', title: 'Create architecture diagram', type: 'canvas', description: 'Design system architecture' },
        2: { id: 'task-validate-1', title: 'Run validation checks', type: 'execute', description: 'Validate requirements' },
        3: { id: 'task-generate-1', title: 'Generate deployment config', type: 'generate', description: 'Create deployment configuration' },
        4: { id: 'task-run-1', title: 'Deploy to environment', type: 'deploy', description: 'Deploy the application' },
        5: { id: 'task-observe-1', title: 'Set up monitoring', type: 'configure', description: 'Configure monitoring dashboard' },
        6: { id: 'task-improve-1', title: 'Review insights', type: 'analyze', description: 'Analyze improvement opportunities' },
    };

    const task = tasks[parseInt(phase as string) || 0];
    res.json(task);
});

app.post('/api/tasks/:taskId/execute', (req, res) => {
    const { taskId } = req.params;

    // Simulate task execution
    setTimeout(() => {
        res.json({
            taskId,
            status: 'completed',
            steps: [
                { id: '1', status: 'completed', output: 'Analyzed context' },
                { id: '2', status: 'completed', output: 'Generated content' },
                { id: '3', status: 'completed', output: 'Validated artifact' }
            ],
            artifacts: [
                { id: 'new-art-1', title: 'Problem Statement v2', type: 'Idea Brief' }
            ],
            logs: [
                'Context loaded...',
                'Draft generated...',
                'Validation checks passed...'
            ]
        });
    }, 1500);
});

// ============================================================================
// AI
// ============================================================================

app.get('/api/projects/:projectId/ai/recommendations', (req, res) => {
    const { phase } = req.query;

    res.json([
        {
            id: randomUUID(),
            title: 'Complete artifact validation',
            message: 'AI suggests completing validation checks before proceeding',
            priority: 'Medium',
            phase: parseInt(phase as string) || 0,
        },
    ]);
});

app.get('/api/projects/:projectId/ai/insights', (req, res) => {
    res.json([
        {
            id: randomUUID(),
            title: 'Pattern detected',
            description: 'Similar projects completed this phase in 3 days',
            confidence: 85,
        },
    ]);
});

app.post('/api/projects/:projectId/ai/validate', (req, res) => {
    const { content } = req.body;

    res.json({
        valid: true,
        suggestions: ['Consider adding more detail to section 2'],
        confidence: 0.92,
    });
});

// ============================================================================
// Audit
// ============================================================================

app.get('/api/projects/:projectId/audit', (req, res) => {
    const { projectId } = req.params;
    const events = db.auditEvents
        .filter(e => e.projectId === projectId)
        .sort((a, b) => b.timestamp - a.timestamp);
    res.json(events);
});

app.post('/api/projects/:projectId/audit', (req, res) => {
    const { projectId } = req.params;
    const event = {
        id: randomUUID(),
        projectId,
        ...req.body,
        timestamp: new Date(),
    };
    db.auditEvents.push(event);
    res.status(201).json(event);
});

// ============================================================================
// Persona
// ============================================================================

app.post('/api/projects/:projectId/persona/derive', (req, res) => {
    const { phase, fowStage } = req.body;

    // Return mock persona
    const personas = {
        0: { persona: 'Product Owner', confidence: 0.85, reason: 'Intent phase requires strategic thinking' },
        1: { persona: 'Architect', confidence: 0.90, reason: 'Shape phase requires technical design' },
        2: { persona: 'QA Engineer', confidence: 0.88, reason: 'Validate phase requires testing expertise' },
        3: { persona: 'Developer', confidence: 0.92, reason: 'Generate phase requires coding skills' },
        4: { persona: 'DevOps Engineer', confidence: 0.89, reason: 'Run phase requires deployment expertise' },
        5: { persona: 'SRE', confidence: 0.87, reason: 'Observe phase requires monitoring expertise' },
        6: { persona: 'Product Manager', confidence: 0.84, reason: 'Improve phase requires strategic planning' },
    };

    const personaData = personas[phase] || personas[0];
    res.json(personaData);
});

// ============================================================================
// DevSecOps Items
// ============================================================================

app.get('/api/projects/:projectId/devsecops', (req, res) => {
    const { projectId } = req.params;
    const items = Array.from(db.devSecOpsItems.values())
        .filter(i => i.projectId === projectId || i.projectId === 'test-project');
    res.json(items);
});

// ============================================================================
// Server
// ============================================================================

app.listen(PORT, () => {
    console.log(`
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║       🚀 Mock API Server for Lifecycle Hub                  ║
║                                                              ║
║       Server running on: http://localhost:${PORT}             ║
║                                                              ║
║       Available endpoints:                                   ║
║       - GET  /api/health                                     ║
║       - GET  /api/projects/:id/artifacts                     ║
║       - GET  /api/projects/:id/gates/:stage                  ║
║       - GET  /api/projects/:id/tasks/next-best              ║
║       - GET  /api/projects/:id/ai/recommendations            ║
║       - GET  /api/projects/:id/audit                         ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
    `);
});
