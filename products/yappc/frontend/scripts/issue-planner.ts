#!/usr/bin/env tsx
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

import * as fs from 'fs';
import * as path from 'path';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
interface Epic {
  id: string;
  title: string;
  description: string;
  section: string;
  status: 'not-started' | 'in-progress' | 'complete' | 'blocked';
  priority: 'critical' | 'high' | 'medium' | 'low';
  estimatedHours: number;
  dependencies: string[];
  acceptanceCriteria: string[];
  tests: {
    unit?: string;
    integration?: string;
    e2e?: string;
  };
  deliverables: string[];
}

/**
 *
 */
interface Task {
  id: string;
  epicId: string;
  title: string;
  description: string;
  status: 'todo' | 'in-progress' | 'done' | 'blocked';
  assignee?: string;
  estimatedHours: number;
  labels: string[];
  dependencies: string[];
  milestone?: string;
}

/**
 *
 */
interface Milestone {
  id: string;
  title: string;
  description: string;
  dueDate?: Date;
  epics: string[];
  progress: number;
  status: 'planned' | 'active' | 'complete';
}

/**
 *
 */
interface DependencyGraph {
  nodes: Array<{
    id: string;
    label: string;
    type: 'epic' | 'task' | 'milestone';
    status: string;
  }>;
  edges: Array<{
    from: string;
    to: string;
    type: 'depends-on' | 'blocks' | 'part-of';
  }>;
}

/**
 *
 */
interface RoadmapSync {
  lastSync: Date;
  epicsTotal: number;
  epicsComplete: number;
  epicsPending: number;
  epicsBlocked: number;
  overallProgress: number;
  upcomingMilestones: Milestone[];
  blockedTasks: Task[];
}

// ============================================================================
// Epic Parser
// ============================================================================

/**
 *
 */
class EpicParser {
  private featureStoriesPath: string;
  
  /**
   *
   */
  constructor(projectRoot: string) {
    this.featureStoriesPath = path.join(projectRoot, 'docs/canvas-feature-stories.md');
  }
  
  /**
   * Parse all epics from canvas-feature-stories.md
   */
  parseEpics(): Epic[] {
    const content = fs.readFileSync(this.featureStoriesPath, 'utf-8');
    const epics: Epic[] = [];
    
    // Match feature sections with ### headings
    const featureRegex = /###\s+([\d.]+)\s+([^\n]+?)(\s+✅\s+\*\*COMPLETE\*\*)?[\s\S]*?\*\*Story\*\*:\s*([^\n]+)[\s\S]*?\*\*Progress\*\*:\s*([^\n]+)[\s\S]*?(?=###|$)/g;
    
    let match;
    while ((match = featureRegex.exec(content)) !== null) {
      const [, id, title, completeMarker, story, progress] = match;
      const fullMatch = match[0];
      
      // Extract section from ID (e.g., "1.16" -> "1")
      const section = id.split('.')[0];
      
      // Determine status
      let status: Epic['status'] = 'not-started';
      if (completeMarker || progress.includes('✅') || progress.includes('Complete')) {
        status = 'complete';
      } else if (progress.includes('In Progress') || progress.includes('🔄')) {
        status = 'in-progress';
      } else if (progress.includes('Blocked') || progress.includes('⏸')) {
        status = 'blocked';
      }
      
      // Extract acceptance criteria
      const criteriaMatch = fullMatch.match(/\*\*Acceptance Criteria\*\*[\s\S]*?(?=\*\*Tests\*\*|###|$)/);
      const acceptanceCriteria: string[] = [];
      if (criteriaMatch) {
        const criteriaText = criteriaMatch[0];
        const criteriaItems = criteriaText.match(/[-*]\s+\*\*([^*]+)\*\*/g) || [];
        acceptanceCriteria.push(...criteriaItems.map(item => 
          item.replace(/[-*]\s+\*\*/, '').replace(/\*\*.*$/, '')
        ));
      }
      
      // Extract tests
      const testsMatch = fullMatch.match(/\*\*Tests\*\*[\s\S]*?(?=\*\*|###|$)/);
      const tests: Epic['tests'] = {};
      if (testsMatch) {
        const testsText = testsMatch[0];
        if (testsText.includes('Unit')) {
          const unitMatch = testsText.match(/[-*]\s+\*\*Unit\*\*[:\s]+([^\n]+)/);
          if (unitMatch) tests.unit = unitMatch[1].trim();
        }
        if (testsText.includes('Integration')) {
          const integrationMatch = testsText.match(/[-*]\s+\*\*Integration\*\*[:\s]+([^\n]+)/);
          if (integrationMatch) tests.integration = integrationMatch[1].trim();
        }
        if (testsText.includes('E2E')) {
          const e2eMatch = testsText.match(/[-*]\s+\*\*E2E\*\*[:\s]+([^\n]+)/);
          if (e2eMatch) tests.e2e = e2eMatch[1].trim();
        }
      }
      
      // Extract deliverables
      const deliverablesMatch = fullMatch.match(/\*\*Deliverables\*\*:?[\s\S]*?(?=\*\*|###|$)/);
      const deliverables: string[] = [];
      if (deliverablesMatch) {
        const deliverablesText = deliverablesMatch[0];
        const items = deliverablesText.match(/\d+\.\s+✅\s+`([^`]+)`/g) || [];
        deliverables.push(...items.map(item => item.match(/`([^`]+)`/)?.[1] || ''));
      }
      
      // Estimate hours based on complexity
      const estimatedHours = this.estimateHours(title, acceptanceCriteria.length, status);
      
      // Determine priority based on section and dependencies
      const priority = this.determinePriority(section, status);
      
      // Extract dependencies from text
      const dependencies = this.extractDependencies(fullMatch);
      
      epics.push({
        id,
        title: title.trim(),
        description: story.trim(),
        section,
        status,
        priority,
        estimatedHours,
        dependencies,
        acceptanceCriteria,
        tests,
        deliverables,
      });
    }
    
    return epics;
  }
  
  /**
   *
   */
  private estimateHours(title: string, criteriaCount: number, status: Epic['status']): number {
    if (status === 'complete') return 0;
    
    // Base estimate on complexity indicators
    let hours = 8; // Base estimate
    
    if (title.includes('API') || title.includes('Architecture')) hours += 16;
    if (title.includes('Integration') || title.includes('System')) hours += 12;
    if (title.includes('Marketplace') || title.includes('Plugin')) hours += 8;
    if (title.includes('Documentation') || title.includes('Guide')) hours = 4;
    
    // Adjust based on acceptance criteria
    hours += criteriaCount * 2;
    
    return Math.min(hours, 40); // Cap at 40 hours (1 week)
  }
  
  /**
   *
   */
  private determinePriority(section: string, status: Epic['status']): Epic['priority'] {
    if (status === 'complete') return 'low';
    if (status === 'blocked') return 'high';
    
    // Section 1 (Current Capabilities) is high priority
    if (section === '1') return 'high';
    
    // Section 7 (Operational Excellence) is critical for production
    if (section === '7') return 'critical';
    
    // Section 8 (Extensibility) is high priority
    if (section === '8') return 'high';
    
    // Other sections are medium
    return 'medium';
  }
  
  /**
   *
   */
  private extractDependencies(text: string): string[] {
    const deps: string[] = [];
    
    // Look for explicit dependency mentions
    const depMatch = text.match(/depends?(?:\s+on)?[:\s]+([^.\n]+)/i);
    if (depMatch) {
      const depText = depMatch[1];
      const depIds = depText.match(/[\d.]+/g) || [];
      deps.push(...depIds);
    }
    
    // Look for "blocked by" mentions
    const blockedMatch = text.match(/blocked\s+(?:by|on)[:\s]+([^.\n]+)/i);
    if (blockedMatch) {
      const blockText = blockedMatch[1];
      const blockIds = blockText.match(/[\d.]+/g) || [];
      deps.push(...blockIds);
    }
    
    return [...new Set(deps)]; // Remove duplicates
  }
}

// ============================================================================
// Task Generator
// ============================================================================

/**
 *
 */
class TaskGenerator {
  /**
   * Break down an epic into implementable tasks
   */
  generateTasks(epic: Epic): Task[] {
    const tasks: Task[] = [];
    
    // Task 1: Design & Architecture (if not complete)
    if (epic.status !== 'complete' && !epic.deliverables.some(d => d.includes('types'))) {
      tasks.push({
        id: `${epic.id}.design`,
        epicId: epic.id,
        title: `Design ${epic.title} Architecture`,
        description: `Design the architecture and create TypeScript types for ${epic.title}`,
        status: 'todo',
        estimatedHours: Math.ceil(epic.estimatedHours * 0.2),
        labels: ['design', 'architecture'],
        dependencies: epic.dependencies,
        milestone: `Section ${epic.section}`,
      });
    }
    
    // Task 2: Implementation
    if (epic.status !== 'complete') {
      tasks.push({
        id: `${epic.id}.implementation`,
        epicId: epic.id,
        title: `Implement ${epic.title}`,
        description: `Core implementation of ${epic.title}:\n\n${epic.acceptanceCriteria.map((c, i) => `${i + 1}. ${c}`).join('\n')}`,
        status: epic.status === 'in-progress' ? 'in-progress' : 'todo',
        estimatedHours: Math.ceil(epic.estimatedHours * 0.5),
        labels: ['implementation', 'feature'],
        dependencies: [`${epic.id}.design`],
        milestone: `Section ${epic.section}`,
      });
    }
    
    // Task 3: Tests
    if (epic.status !== 'complete' && (epic.tests.unit || epic.tests.integration)) {
      tasks.push({
        id: `${epic.id}.tests`,
        epicId: epic.id,
        title: `Test ${epic.title}`,
        description: `Write comprehensive tests for ${epic.title}:\n\n- Unit tests: ${epic.tests.unit || 'TBD'}\n- Integration tests: ${epic.tests.integration || 'TBD'}`,
        status: 'todo',
        estimatedHours: Math.ceil(epic.estimatedHours * 0.2),
        labels: ['testing', 'quality'],
        dependencies: [`${epic.id}.implementation`],
        milestone: `Section ${epic.section}`,
      });
    }
    
    // Task 4: Documentation
    if (epic.status !== 'complete' && !epic.deliverables.some(d => d.includes('README'))) {
      tasks.push({
        id: `${epic.id}.docs`,
        epicId: epic.id,
        title: `Document ${epic.title}`,
        description: `Create comprehensive documentation for ${epic.title} including API reference, examples, and best practices`,
        status: 'todo',
        estimatedHours: Math.ceil(epic.estimatedHours * 0.1),
        labels: ['documentation'],
        dependencies: [`${epic.id}.implementation`],
        milestone: `Section ${epic.section}`,
      });
    }
    
    return tasks;
  }
}

// ============================================================================
// Dependency Tracker
// ============================================================================

/**
 *
 */
class DependencyTracker {
  /**
   * Build dependency graph for epics and tasks
   */
  buildGraph(epics: Epic[], tasks: Task[]): DependencyGraph {
    const nodes: DependencyGraph['nodes'] = [];
    const edges: DependencyGraph['edges'] = [];
    
    // Add epic nodes
    for (const epic of epics) {
      nodes.push({
        id: epic.id,
        label: epic.title,
        type: 'epic',
        status: epic.status,
      });
      
      // Add dependency edges
      for (const depId of epic.dependencies) {
        edges.push({
          from: epic.id,
          to: depId,
          type: 'depends-on',
        });
      }
    }
    
    // Add task nodes
    for (const task of tasks) {
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
      for (const depId of task.dependencies) {
        edges.push({
          from: task.id,
          to: depId,
          type: 'depends-on',
        });
      }
    }
    
    return { nodes, edges };
  }
  
  /**
   * Find all blocked items in the graph
   */
  findBlocked(graph: DependencyGraph): Array<{ id: string; label: string; blockedBy: string[] }> {
    const blocked: Array<{ id: string; label: string; blockedBy: string[] }> = [];
    
    for (const node of graph.nodes) {
      if (node.status === 'blocked' || node.status === 'todo') {
        const blockedBy = graph.edges
          .filter(e => e.from === node.id && e.type === 'depends-on')
          .map(e => e.to)
          .filter(depId => {
            const depNode = graph.nodes.find(n => n.id === depId);
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
    }
    
    return blocked;
  }
  
  /**
   * Generate Mermaid diagram for dependency graph
   */
  generateMermaidDiagram(graph: DependencyGraph): string {
    let mermaid = 'graph TD\n';
    
    // Add nodes with styling based on status
    for (const node of graph.nodes) {
      const style = this.getNodeStyle(node.status);
      const shape = node.type === 'epic' ? `[${node.label}]` : `(${node.label})`;
      mermaid += `  ${node.id}${shape}\n`;
      if (style) {
        mermaid += `  style ${node.id} ${style}\n`;
      }
    }
    
    // Add edges
    for (const edge of graph.edges) {
      const arrow = edge.type === 'depends-on' ? '-->|depends on|' : 
                    edge.type === 'blocks' ? '-.->|blocks|' : 
                    '---|part of|';
      mermaid += `  ${edge.from} ${arrow} ${edge.to}\n`;
    }
    
    return mermaid;
  }
  
  /**
   *
   */
  private getNodeStyle(status: string): string {
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
  }
}

// ============================================================================
// Roadmap Sync
// ============================================================================

/**
 *
 */
class RoadmapSync {
  /**
   * Sync roadmap progress with implementation status
   */
  syncProgress(epics: Epic[]): RoadmapSync {
    const total = epics.length;
    const complete = epics.filter(e => e.status === 'complete').length;
    const pending = epics.filter(e => e.status === 'not-started' || e.status === 'in-progress').length;
    const blocked = epics.filter(e => e.status === 'blocked').length;
    
    const progress = total > 0 ? Math.round((complete / total) * 100) : 0;
    
    // Group epics by section to create milestones
    const sections = new Map<string, Epic[]>();
    for (const epic of epics) {
      if (!sections.has(epic.section)) {
        sections.set(epic.section, []);
      }
      sections.get(epic.section)!.push(epic);
    }
    
    const milestones: Milestone[] = [];
    for (const [section, sectionEpics] of sections) {
      const sectionComplete = sectionEpics.filter(e => e.status === 'complete').length;
      const sectionProgress = Math.round((sectionComplete / sectionEpics.length) * 100);
      
      let status: Milestone['status'] = 'planned';
      if (sectionProgress === 100) status = 'complete';
      else if (sectionProgress > 0) status = 'active';
      
      milestones.push({
        id: `section-${section}`,
        title: `Section ${section}`,
        description: `Canvas features section ${section}`,
        epics: sectionEpics.map(e => e.id),
        progress: sectionProgress,
        status,
      });
    }
    
    // Find upcoming milestones (active but not complete)
    const upcomingMilestones = milestones
      .filter(m => m.status === 'active')
      .sort((a, b) => b.progress - a.progress);
    
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
  }
  
  /**
   * Generate progress report
   */
  generateReport(sync: RoadmapSync, epics: Epic[]): string {
    let report = '# Canvas Roadmap Progress Report\n\n';
    report += `**Generated**: ${sync.lastSync.toISOString()}\n\n`;
    report += `## Overall Progress: ${sync.overallProgress}%\n\n`;
    report += `- Total Epics: ${sync.epicsTotal}\n`;
    report += `- ✅ Complete: ${sync.epicsComplete}\n`;
    report += `- 🔄 Pending: ${sync.epicsPending}\n`;
    report += `- ⏸️ Blocked: ${sync.epicsBlocked}\n\n`;
    
    report += '## Milestones\n\n';
    for (const milestone of sync.upcomingMilestones) {
      const icon = milestone.status === 'complete' ? '✅' : milestone.status === 'active' ? '🔄' : '📋';
      report += `### ${icon} ${milestone.title} (${milestone.progress}%)\n\n`;
      
      const milestoneEpics = epics.filter(e => milestone.epics.includes(e.id));
      for (const epic of milestoneEpics) {
        const statusIcon = epic.status === 'complete' ? '✅' : 
                          epic.status === 'in-progress' ? '🔄' : 
                          epic.status === 'blocked' ? '⏸️' : '📋';
        report += `- ${statusIcon} ${epic.id} ${epic.title}\n`;
      }
      report += '\n';
    }
    
    if (sync.epicsBlocked > 0) {
      report += '## ⚠️ Blocked Epics\n\n';
      const blockedEpics = epics.filter(e => e.status === 'blocked');
      for (const epic of blockedEpics) {
        report += `- **${epic.id} ${epic.title}**\n`;
        if (epic.dependencies.length > 0) {
          report += `  - Blocked by: ${epic.dependencies.join(', ')}\n`;
        }
      }
      report += '\n';
    }
    
    return report;
  }
}

// ============================================================================
// Main CLI
// ============================================================================

/**
 *
 */
async function main() {
  const projectRoot = process.cwd();
  
  console.log('🔍 Canvas Issue Planning Workflow\n');
  
  // Parse epics
  console.log('📖 Parsing epics from canvas-feature-stories.md...');
  const parser = new EpicParser(projectRoot);
  const epics = parser.parseEpics();
  console.log(`✅ Found ${epics.length} epics\n`);
  
  // Generate tasks
  console.log('📋 Breaking down epics into tasks...');
  const generator = new TaskGenerator();
  const allTasks: Task[] = [];
  for (const epic of epics) {
    const tasks = generator.generateTasks(epic);
    allTasks.push(...tasks);
  }
  console.log(`✅ Generated ${allTasks.length} tasks\n`);
  
  // Build dependency graph
  console.log('🔗 Building dependency graph...');
  const tracker = new DependencyTracker();
  const graph = tracker.buildGraph(epics, allTasks);
  console.log(`✅ Graph has ${graph.nodes.length} nodes and ${graph.edges.length} edges\n`);
  
  // Find blocked items
  const blocked = tracker.findBlocked(graph);
  if (blocked.length > 0) {
    console.log(`⚠️  Found ${blocked.length} blocked items:\n`);
    for (const item of blocked) {
      console.log(`  - ${item.id}: ${item.label}`);
      console.log(`    Blocked by: ${item.blockedBy.join(', ')}\n`);
    }
  }
  
  // Sync roadmap
  console.log('📊 Syncing roadmap progress...');
  const roadmapSync = new RoadmapSync();
  const syncResult = roadmapSync.syncProgress(epics);
  console.log(`✅ Overall progress: ${syncResult.overallProgress}%\n`);
  
  // Generate outputs
  const outputDir = path.join(projectRoot, 'docs/planning');
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }
  
  // Save epics JSON
  console.log('💾 Saving outputs...');
  fs.writeFileSync(
    path.join(outputDir, 'epics.json'),
    JSON.stringify(epics, null, 2)
  );
  
  // Save tasks JSON
  fs.writeFileSync(
    path.join(outputDir, 'tasks.json'),
    JSON.stringify(allTasks, null, 2)
  );
  
  // Save dependency graph JSON
  fs.writeFileSync(
    path.join(outputDir, 'dependency-graph.json'),
    JSON.stringify(graph, null, 2)
  );
  
  // Save Mermaid diagram
  const mermaidDiagram = tracker.generateMermaidDiagram(graph);
  fs.writeFileSync(
    path.join(outputDir, 'dependency-graph.mmd'),
    mermaidDiagram
  );
  
  // Save progress report
  const report = roadmapSync.generateReport(syncResult, epics);
  fs.writeFileSync(
    path.join(outputDir, 'roadmap-progress.md'),
    report
  );
  
  console.log(`✅ Saved outputs to ${outputDir}/\n`);
  
  // Summary
  console.log('📈 Summary:');
  console.log(`  - Epics: ${epics.length} (${syncResult.epicsComplete} complete, ${syncResult.epicsPending} pending, ${syncResult.epicsBlocked} blocked)`);
  console.log(`  - Tasks: ${allTasks.length}`);
  console.log(`  - Milestones: ${syncResult.upcomingMilestones.length} active`);
  console.log(`  - Overall Progress: ${syncResult.overallProgress}%`);
  console.log('\n✨ Issue planning complete!\n');
}

// Run if called directly
if (require.main === module) {
  main().catch(console.error);
}

export { EpicParser, TaskGenerator, DependencyTracker, RoadmapSync };
export type { Epic, Task, Milestone, DependencyGraph };
