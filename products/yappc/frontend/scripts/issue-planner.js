#!/usr/bin/env tsx
"use strict";
/**
 * Issue Planning Workflow
 *
 * Automated epic breakdown, milestone dependencies, and roadmap sync for project planning.
 *
 * Features:
 * - Parse feature stories from canvas-feature-stories.md
 * - Break down epics into implementable tasks
 * - Track dependencies between features and milestones
 * - Generate GitHub issues with proper labels and milestones
 * - Sync roadmap progress with implementation status
 * - Generate dependency graphs and timeline visualizations
 */
const __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P((resolve) => { resolve(value); }); }
    return new (P || (P = Promise))((resolve, reject) => {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
const __generator = (this && this.__generator) || function (thisArg, body) {
    let _ = { label: 0, sent() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
const __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.RoadmapSync = exports.DependencyTracker = exports.TaskGenerator = exports.EpicParser = void 0;
const fs = require("fs");
const path = require("path");
// ============================================================================
// Epic Parser
// ============================================================================
const EpicParser = /** @class */ (function () {
    function EpicParser(projectRoot) {
        this.featureStoriesPath = path.join(projectRoot, 'docs/canvas-feature-stories.md');
    }
    /**
     * Parse all epics from canvas-feature-stories.md
     */
    EpicParser.prototype.parseEpics = function () {
        const content = fs.readFileSync(this.featureStoriesPath, 'utf-8');
        const epics = [];
        // Match feature sections with ### headings
        const featureRegex = /###\s+([\d.]+)\s+([^\n]+?)(\s+✅\s+\*\*COMPLETE\*\*)?[\s\S]*?\*\*Story\*\*:\s*([^\n]+)[\s\S]*?\*\*Progress\*\*:\s*([^\n]+)[\s\S]*?(?=###|$)/g;
        let match;
        while ((match = featureRegex.exec(content)) !== null) {
            const id = match[1], title = match[2], completeMarker = match[3], story = match[4], progress = match[5];
            const fullMatch = match[0];
            // Extract section from ID (e.g., "1.16" -> "1")
            const section = id.split('.')[0];
            // Determine status
            let status_1 = 'not-started';
            if (completeMarker || progress.includes('✅') || progress.includes('Complete')) {
                status_1 = 'complete';
            }
            else if (progress.includes('In Progress') || progress.includes('🔄')) {
                status_1 = 'in-progress';
            }
            else if (progress.includes('Blocked') || progress.includes('⏸')) {
                status_1 = 'blocked';
            }
            // Extract acceptance criteria
            const criteriaMatch = fullMatch.match(/\*\*Acceptance Criteria\*\*[\s\S]*?(?=\*\*Tests\*\*|###|$)/);
            const acceptanceCriteria = [];
            if (criteriaMatch) {
                const criteriaText = criteriaMatch[0];
                const criteriaItems = criteriaText.match(/[-*]\s+\*\*([^*]+)\*\*/g) || [];
                acceptanceCriteria.push.apply(acceptanceCriteria, criteriaItems.map((item) => {
                    return item.replace(/[-*]\s+\*\*/, '').replace(/\*\*.*$/, '');
                }));
            }
            // Extract tests
            const testsMatch = fullMatch.match(/\*\*Tests\*\*[\s\S]*?(?=\*\*|###|$)/);
            const tests = {};
            if (testsMatch) {
                const testsText = testsMatch[0];
                if (testsText.includes('Unit')) {
                    const unitMatch = testsText.match(/[-*]\s+\*\*Unit\*\*[:\s]+([^\n]+)/);
                    if (unitMatch)
                        tests.unit = unitMatch[1].trim();
                }
                if (testsText.includes('Integration')) {
                    const integrationMatch = testsText.match(/[-*]\s+\*\*Integration\*\*[:\s]+([^\n]+)/);
                    if (integrationMatch)
                        tests.integration = integrationMatch[1].trim();
                }
                if (testsText.includes('E2E')) {
                    const e2eMatch = testsText.match(/[-*]\s+\*\*E2E\*\*[:\s]+([^\n]+)/);
                    if (e2eMatch)
                        tests.e2e = e2eMatch[1].trim();
                }
            }
            // Extract deliverables
            const deliverablesMatch = fullMatch.match(/\*\*Deliverables\*\*:?[\s\S]*?(?=\*\*|###|$)/);
            const deliverables = [];
            if (deliverablesMatch) {
                const deliverablesText = deliverablesMatch[0];
                const items = deliverablesText.match(/\d+\.\s+✅\s+`([^`]+)`/g) || [];
                deliverables.push.apply(deliverables, items.map((item) => { let _a; return ((_a = item.match(/`([^`]+)`/)) === null || _a === void 0 ? void 0 : _a[1]) || ''; }));
            }
            // Estimate hours based on complexity
            const estimatedHours = this.estimateHours(title, acceptanceCriteria.length, status_1);
            // Determine priority based on section and dependencies
            const priority = this.determinePriority(section, status_1);
            // Extract dependencies from text
            const dependencies = this.extractDependencies(fullMatch);
            epics.push({
                id,
                title: title.trim(),
                description: story.trim(),
                section,
                status: status_1,
                priority,
                estimatedHours,
                dependencies,
                acceptanceCriteria,
                tests,
                deliverables,
            });
        }
        return epics;
    };
    EpicParser.prototype.estimateHours = function (title, criteriaCount, status) {
        if (status === 'complete')
            return 0;
        // Base estimate on complexity indicators
        let hours = 8; // Base estimate
        if (title.includes('API') || title.includes('Architecture'))
            hours += 16;
        if (title.includes('Integration') || title.includes('System'))
            hours += 12;
        if (title.includes('Marketplace') || title.includes('Plugin'))
            hours += 8;
        if (title.includes('Documentation') || title.includes('Guide'))
            hours = 4;
        // Adjust based on acceptance criteria
        hours += criteriaCount * 2;
        return Math.min(hours, 40); // Cap at 40 hours (1 week)
    };
    EpicParser.prototype.determinePriority = function (section, status) {
        if (status === 'complete')
            return 'low';
        if (status === 'blocked')
            return 'high';
        // Section 1 (Current Capabilities) is high priority
        if (section === '1')
            return 'high';
        // Section 7 (Operational Excellence) is critical for production
        if (section === '7')
            return 'critical';
        // Section 8 (Extensibility) is high priority
        if (section === '8')
            return 'high';
        // Other sections are medium
        return 'medium';
    };
    EpicParser.prototype.extractDependencies = function (text) {
        const deps = [];
        // Look for explicit dependency mentions
        const depMatch = text.match(/depends?(?:\s+on)?[:\s]+([^.\n]+)/i);
        if (depMatch) {
            const depText = depMatch[1];
            const depIds = depText.match(/[\d.]+/g) || [];
            deps.push.apply(deps, depIds);
        }
        // Look for "blocked by" mentions
        const blockedMatch = text.match(/blocked\s+(?:by|on)[:\s]+([^.\n]+)/i);
        if (blockedMatch) {
            const blockText = blockedMatch[1];
            const blockIds = blockText.match(/[\d.]+/g) || [];
            deps.push.apply(deps, blockIds);
        }
        return __spreadArray([], new Set(deps), true); // Remove duplicates
    };
    return EpicParser;
}());
exports.EpicParser = EpicParser;
// ============================================================================
// Task Generator
// ============================================================================
const TaskGenerator = /** @class */ (function () {
    function TaskGenerator() {
    }
    /**
     * Break down an epic into implementable tasks
     */
    TaskGenerator.prototype.generateTasks = function (epic) {
        const tasks = [];
        // Task 1: Design & Architecture (if not complete)
        if (epic.status !== 'complete' && !epic.deliverables.some((d) => { return d.includes('types'); })) {
            tasks.push({
                id: "".concat(epic.id, ".design"),
                epicId: epic.id,
                title: "Design ".concat(epic.title, " Architecture"),
                description: "Design the architecture and create TypeScript types for ".concat(epic.title),
                status: 'todo',
                estimatedHours: Math.ceil(epic.estimatedHours * 0.2),
                labels: ['design', 'architecture'],
                dependencies: epic.dependencies,
                milestone: "Section ".concat(epic.section),
            });
        }
        // Task 2: Implementation
        if (epic.status !== 'complete') {
            tasks.push({
                id: "".concat(epic.id, ".implementation"),
                epicId: epic.id,
                title: "Implement ".concat(epic.title),
                description: "Core implementation of ".concat(epic.title, ":\n\n").concat(epic.acceptanceCriteria.map((c, i) => { return "".concat(i + 1, ". ").concat(c); }).join('\n')),
                status: epic.status === 'in-progress' ? 'in-progress' : 'todo',
                estimatedHours: Math.ceil(epic.estimatedHours * 0.5),
                labels: ['implementation', 'feature'],
                dependencies: ["".concat(epic.id, ".design")],
                milestone: "Section ".concat(epic.section),
            });
        }
        // Task 3: Tests
        if (epic.status !== 'complete' && (epic.tests.unit || epic.tests.integration)) {
            tasks.push({
                id: "".concat(epic.id, ".tests"),
                epicId: epic.id,
                title: "Test ".concat(epic.title),
                description: "Write comprehensive tests for ".concat(epic.title, ":\n\n- Unit tests: ").concat(epic.tests.unit || 'TBD', "\n- Integration tests: ").concat(epic.tests.integration || 'TBD'),
                status: 'todo',
                estimatedHours: Math.ceil(epic.estimatedHours * 0.2),
                labels: ['testing', 'quality'],
                dependencies: ["".concat(epic.id, ".implementation")],
                milestone: "Section ".concat(epic.section),
            });
        }
        // Task 4: Documentation
        if (epic.status !== 'complete' && !epic.deliverables.some((d) => { return d.includes('README'); })) {
            tasks.push({
                id: "".concat(epic.id, ".docs"),
                epicId: epic.id,
                title: "Document ".concat(epic.title),
                description: "Create comprehensive documentation for ".concat(epic.title, " including API reference, examples, and best practices"),
                status: 'todo',
                estimatedHours: Math.ceil(epic.estimatedHours * 0.1),
                labels: ['documentation'],
                dependencies: ["".concat(epic.id, ".implementation")],
                milestone: "Section ".concat(epic.section),
            });
        }
        return tasks;
    };
    return TaskGenerator;
}());
exports.TaskGenerator = TaskGenerator;
// ============================================================================
// Dependency Tracker
// ============================================================================
const DependencyTracker = /** @class */ (function () {
    function DependencyTracker() {
    }
    /**
     * Build dependency graph for epics and tasks
     */
    DependencyTracker.prototype.buildGraph = function (epics, tasks) {
        const nodes = [];
        const edges = [];
        // Add epic nodes
        for (let _i = 0, epics_1 = epics; _i < epics_1.length; _i++) {
            const epic = epics_1[_i];
            nodes.push({
                id: epic.id,
                label: epic.title,
                type: 'epic',
                status: epic.status,
            });
            // Add dependency edges
            for (let _a = 0, _b = epic.dependencies; _a < _b.length; _a++) {
                var depId = _b[_a];
                edges.push({
                    from: epic.id,
                    to: depId,
                    type: 'depends-on',
                });
            }
        }
        // Add task nodes
        for (let _c = 0, tasks_1 = tasks; _c < tasks_1.length; _c++) {
            const task = tasks_1[_c];
            nodes.push({
                id: task.id,
                label: task.title,
                type: 'task',
                status: task.status,
            });
            // Link task to epic
            edges.push({
                from: task.id,
                to: task.epicId,
                type: 'part-of',
            });
            // Add task dependencies
            for (let _d = 0, _e = task.dependencies; _d < _e.length; _d++) {
                var depId = _e[_d];
                edges.push({
                    from: task.id,
                    to: depId,
                    type: 'depends-on',
                });
            }
        }
        return { nodes, edges };
    };
    /**
     * Find all blocked items in the graph
     */
    DependencyTracker.prototype.findBlocked = function (graph) {
        const blocked = [];
        const _loop_1 = function (node) {
            if (node.status === 'blocked' || node.status === 'todo') {
                const blockedBy = graph.edges
                    .filter((e) => { return e.from === node.id && e.type === 'depends-on'; })
                    .map((e) => { return e.to; })
                    .filter((depId) => {
                    const depNode = graph.nodes.find((n) => { return n.id === depId; });
                    return depNode && depNode.status !== 'done' && depNode.status !== 'complete';
                });
                if (blockedBy.length > 0) {
                    blocked.push({
                        id: node.id,
                        label: node.label,
                        blockedBy,
                    });
                }
            }
        };
        for (let _i = 0, _a = graph.nodes; _i < _a.length; _i++) {
            const node = _a[_i];
            _loop_1(node);
        }
        return blocked;
    };
    /**
     * Generate Mermaid diagram for dependency graph
     */
    DependencyTracker.prototype.generateMermaidDiagram = function (graph) {
        let mermaid = 'graph TD\n';
        // Add nodes with styling based on status
        for (let _i = 0, _a = graph.nodes; _i < _a.length; _i++) {
            const node = _a[_i];
            const style = this.getNodeStyle(node.status);
            const shape = node.type === 'epic' ? "[".concat(node.label, "]") : "(".concat(node.label, ")");
            mermaid += "  ".concat(node.id).concat(shape, "\n");
            if (style) {
                mermaid += "  style ".concat(node.id, " ").concat(style, "\n");
            }
        }
        // Add edges
        for (let _b = 0, _c = graph.edges; _b < _c.length; _b++) {
            const edge = _c[_b];
            const arrow = edge.type === 'depends-on' ? '-->|depends on|' :
                edge.type === 'blocks' ? '-.->|blocks|' :
                    '---|part of|';
            mermaid += "  ".concat(edge.from, " ").concat(arrow, " ").concat(edge.to, "\n");
        }
        return mermaid;
    };
    DependencyTracker.prototype.getNodeStyle = function (status) {
        switch (status) {
            case 'complete':
            case 'done':
                return 'fill:#4CAF50,color:#fff';
            case 'in-progress':
                return 'fill:#2196F3,color:#fff';
            case 'blocked':
                return 'fill:#F44336,color:#fff';
            default:
                return 'fill:#9E9E9E,color:#fff';
        }
    };
    return DependencyTracker;
}());
exports.DependencyTracker = DependencyTracker;
// ============================================================================
// Roadmap Sync
// ============================================================================
const RoadmapSync = /** @class */ (function () {
    function RoadmapSync() {
    }
    /**
     * Sync roadmap progress with implementation status
     */
    RoadmapSync.prototype.syncProgress = function (epics) {
        const total = epics.length;
        const complete = epics.filter((e) => { return e.status === 'complete'; }).length;
        const pending = epics.filter((e) => { return e.status === 'not-started' || e.status === 'in-progress'; }).length;
        const blocked = epics.filter((e) => { return e.status === 'blocked'; }).length;
        const progress = total > 0 ? Math.round((complete / total) * 100) : 0;
        // Group epics by section to create milestones
        const sections = new Map();
        for (let _i = 0, epics_2 = epics; _i < epics_2.length; _i++) {
            const epic = epics_2[_i];
            if (!sections.has(epic.section)) {
                sections.set(epic.section, []);
            }
            sections.get(epic.section).push(epic);
        }
        const milestones = [];
        for (let _a = 0, sections_1 = sections; _a < sections_1.length; _a++) {
            const _b = sections_1[_a], section = _b[0], sectionEpics = _b[1];
            const sectionComplete = sectionEpics.filter((e) => { return e.status === 'complete'; }).length;
            const sectionProgress = Math.round((sectionComplete / sectionEpics.length) * 100);
            let status_2 = 'planned';
            if (sectionProgress === 100)
                status_2 = 'complete';
            else if (sectionProgress > 0)
                status_2 = 'active';
            milestones.push({
                id: "section-".concat(section),
                title: "Section ".concat(section),
                description: "Canvas features section ".concat(section),
                epics: sectionEpics.map((e) => { return e.id; }),
                progress: sectionProgress,
                status: status_2,
            });
        }
        // Find upcoming milestones (active but not complete)
        const upcomingMilestones = milestones
            .filter((m) => { return m.status === 'active'; })
            .sort((a, b) => { return b.progress - a.progress; });
        return {
            lastSync: new Date(),
            epicsTotal: total,
            epicsComplete: complete,
            epicsPending: pending,
            epicsBlocked: blocked,
            overallProgress: progress,
            upcomingMilestones,
            blockedTasks: [], // Would be populated from tasks
        };
    };
    /**
     * Generate progress report
     */
    RoadmapSync.prototype.generateReport = function (sync, epics) {
        let report = '# Canvas Roadmap Progress Report\n\n';
        report += "**Generated**: ".concat(sync.lastSync.toISOString(), "\n\n");
        report += "## Overall Progress: ".concat(sync.overallProgress, "%\n\n");
        report += "- Total Epics: ".concat(sync.epicsTotal, "\n");
        report += "- \u2705 Complete: ".concat(sync.epicsComplete, "\n");
        report += "- \uD83D\uDD04 Pending: ".concat(sync.epicsPending, "\n");
        report += "- \u23F8\uFE0F Blocked: ".concat(sync.epicsBlocked, "\n\n");
        report += '## Milestones\n\n';
        const _loop_2 = function (milestone) {
            const icon = milestone.status === 'complete' ? '✅' : milestone.status === 'active' ? '🔄' : '📋';
            report += "### ".concat(icon, " ").concat(milestone.title, " (").concat(milestone.progress, "%)\n\n");
            const milestoneEpics = epics.filter((e) => { return milestone.epics.includes(e.id); });
            for (let _c = 0, milestoneEpics_1 = milestoneEpics; _c < milestoneEpics_1.length; _c++) {
                const epic = milestoneEpics_1[_c];
                const statusIcon = epic.status === 'complete' ? '✅' :
                    epic.status === 'in-progress' ? '🔄' :
                        epic.status === 'blocked' ? '⏸️' : '📋';
                report += "- ".concat(statusIcon, " ").concat(epic.id, " ").concat(epic.title, "\n");
            }
            report += '\n';
        };
        for (let _i = 0, _a = sync.upcomingMilestones; _i < _a.length; _i++) {
            const milestone = _a[_i];
            _loop_2(milestone);
        }
        if (sync.epicsBlocked > 0) {
            report += '## ⚠️ Blocked Epics\n\n';
            const blockedEpics = epics.filter((e) => { return e.status === 'blocked'; });
            for (let _b = 0, blockedEpics_1 = blockedEpics; _b < blockedEpics_1.length; _b++) {
                const epic = blockedEpics_1[_b];
                report += "- **".concat(epic.id, " ").concat(epic.title, "**\n");
                if (epic.dependencies.length > 0) {
                    report += "  - Blocked by: ".concat(epic.dependencies.join(', '), "\n");
                }
            }
            report += '\n';
        }
        return report;
    };
    return RoadmapSync;
}());
exports.RoadmapSync = RoadmapSync;
// ============================================================================
// Main CLI
// ============================================================================
function main() {
    return __awaiter(this, void 0, void 0, function () {
        let projectRoot, parser, epics, generator, allTasks, _i, epics_3, epic, tasks, tracker, graph, blocked, _a, blocked_1, item, roadmapSync, syncResult, outputDir, mermaidDiagram, report;
        return __generator(this, (_b) => {
            projectRoot = process.cwd();
            console.log('🔍 Canvas Issue Planning Workflow\n');
            // Parse epics
            console.log('📖 Parsing epics from canvas-feature-stories.md...');
            parser = new EpicParser(projectRoot);
            epics = parser.parseEpics();
            console.log("\u2705 Found ".concat(epics.length, " epics\n"));
            // Generate tasks
            console.log('📋 Breaking down epics into tasks...');
            generator = new TaskGenerator();
            allTasks = [];
            for (_i = 0, epics_3 = epics; _i < epics_3.length; _i++) {
                epic = epics_3[_i];
                tasks = generator.generateTasks(epic);
                allTasks.push.apply(allTasks, tasks);
            }
            console.log("\u2705 Generated ".concat(allTasks.length, " tasks\n"));
            // Build dependency graph
            console.log('🔗 Building dependency graph...');
            tracker = new DependencyTracker();
            graph = tracker.buildGraph(epics, allTasks);
            console.log("\u2705 Graph has ".concat(graph.nodes.length, " nodes and ").concat(graph.edges.length, " edges\n"));
            blocked = tracker.findBlocked(graph);
            if (blocked.length > 0) {
                console.log("\u26A0\uFE0F  Found ".concat(blocked.length, " blocked items:\n"));
                for (_a = 0, blocked_1 = blocked; _a < blocked_1.length; _a++) {
                    item = blocked_1[_a];
                    console.log("  - ".concat(item.id, ": ").concat(item.label));
                    console.log("    Blocked by: ".concat(item.blockedBy.join(', '), "\n"));
                }
            }
            // Sync roadmap
            console.log('📊 Syncing roadmap progress...');
            roadmapSync = new RoadmapSync();
            syncResult = roadmapSync.syncProgress(epics);
            console.log("\u2705 Overall progress: ".concat(syncResult.overallProgress, "%\n"));
            outputDir = path.join(projectRoot, 'docs/planning');
            if (!fs.existsSync(outputDir)) {
                fs.mkdirSync(outputDir, { recursive: true });
            }
            // Save epics JSON
            console.log('💾 Saving outputs...');
            fs.writeFileSync(path.join(outputDir, 'epics.json'), JSON.stringify(epics, null, 2));
            // Save tasks JSON
            fs.writeFileSync(path.join(outputDir, 'tasks.json'), JSON.stringify(allTasks, null, 2));
            // Save dependency graph JSON
            fs.writeFileSync(path.join(outputDir, 'dependency-graph.json'), JSON.stringify(graph, null, 2));
            mermaidDiagram = tracker.generateMermaidDiagram(graph);
            fs.writeFileSync(path.join(outputDir, 'dependency-graph.mmd'), mermaidDiagram);
            report = roadmapSync.generateReport(syncResult, epics);
            fs.writeFileSync(path.join(outputDir, 'roadmap-progress.md'), report);
            console.log("\u2705 Saved outputs to ".concat(outputDir, "/\n"));
            // Summary
            console.log('📈 Summary:');
            console.log("  - Epics: ".concat(epics.length, " (").concat(syncResult.epicsComplete, " complete, ").concat(syncResult.epicsPending, " pending, ").concat(syncResult.epicsBlocked, " blocked)"));
            console.log("  - Tasks: ".concat(allTasks.length));
            console.log("  - Milestones: ".concat(syncResult.upcomingMilestones.length, " active"));
            console.log("  - Overall Progress: ".concat(syncResult.overallProgress, "%"));
            console.log('\n✨ Issue planning complete!\n');
            return [2 /*return*/];
        });
    });
}
// Run if called directly
if (require.main === module) {
    main().catch(console.error);
}
