#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = process.cwd();
const configPath = path.join(repoRoot, 'config/kernel-implementation-task-matrix.json');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'kernel-implementation-task-matrix.json');
const RETRYABLE_WRITE_CODES = new Set(['UNKNOWN', 'EACCES', 'EBUSY', 'EPERM']);

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function parsePlanTasks(planSource) {
  const tableTasks = parsePlanTasksFromTodoTable(planSource);
  if (tableTasks.length > 0) {
    return tableTasks;
  }

  const phrPriorityTableTasks = parsePlanTasksFromPhrPriorityTable(planSource);
  if (phrPriorityTableTasks.length > 0) {
    return phrPriorityTableTasks;
  }

  // Backward-compatible parser for the legacy numbered-list plan format.
  const itemRegex = /^\s*(\d+)\.\s+\*\*(.+?)\*\*\s*$/gm;
  const matches = [];
  let match;

  while ((match = itemRegex.exec(planSource)) !== null) {
    matches.push({
      id: Number(match[1]),
      title: match[2].trim(),
      start: match.index,
    });
  }

  return matches.map((entry, index) => {
    const end = index + 1 < matches.length ? matches[index + 1].start : planSource.length;
    const block = planSource.slice(entry.start, end);
    const whereLine = block.match(/\*\*Where:\*\*\s*(.+)/);
    const whereRefs = [];

    if (whereLine?.[1]) {
      const backtickRefs = [...whereLine[1].matchAll(/`([^`]+)`/g)].map((ref) => ref[1].trim());
      const rawRefs = backtickRefs.length > 0 ? backtickRefs : whereLine[1]
        .split(',')
        .map((segment) => segment.replace(/[.*]/g, '').trim())
        .filter(Boolean);
      for (const ref of rawRefs) {
        whereRefs.push(ref);
      }
    }

    return {
      id: entry.id,
      title: entry.title,
      whereRefs,
    };
  });
}

function parsePlanTasksFromPhrPriorityTable(planSource) {
  const sectionHeader = '## PHR implementation plan';
  const sectionStart = planSource.indexOf(sectionHeader);
  if (sectionStart < 0) {
    return [];
  }

  const sectionBodyStart = sectionStart + sectionHeader.length;
  const nextHeadingMatch = /^##\s+/m.exec(planSource.slice(sectionBodyStart));
  const sectionEnd = nextHeadingMatch
    ? sectionBodyStart + nextHeadingMatch.index
    : planSource.length;

  const section = planSource.slice(sectionBodyStart, sectionEnd);
  const rows = section.split(/\r?\n/);
  const tasks = [];
  let id = 1;

  for (const row of rows) {
    const trimmed = row.trim();
    if (!trimmed.startsWith('|')) {
      continue;
    }
    if (/^\|\s*-+/.test(trimmed)) {
      continue;
    }

    const columns = trimmed
      .split('|')
      .slice(1, -1)
      .map((column) => column.trim());

    if (columns.length < 4) {
      continue;
    }
    if (columns[0] === 'Priority' || columns[1] === 'Work item') {
      continue;
    }

    // The task matrix tracks current release work; keep P0/P1 tasks from the PHR section.
    if (!['P0', 'P1'].includes(columns[0])) {
      continue;
    }

    const whereColumn = columns[3] ?? '';
    const whereRefs = [...whereColumn.matchAll(/`([^`]+)`/g)].map((token) => token[1].trim());
    tasks.push({
      id,
      title: columns[1],
      whereRefs,
    });
    id += 1;
  }

  return tasks;
}

function parsePlanTasksFromTodoTable(planSource) {
  const tasks = [];
  const lines = planSource.split(/\r?\n/);
  const legacyRegex = /^\|\s*TODO-(\d{3})\s*\|/;
  const idRegex = /^([A-Z]+)-(\d{3})$/;
  const modernIdRegex = /^[A-Z]+(?:-[A-Z0-9]+)+$/;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('|')) {
      continue;
    }
    if (/^\|\s*-+/.test(trimmed)) {
      continue;
    }

    if (legacyRegex.test(trimmed)) {
      const match = trimmed.match(/^\|\s*TODO-(\d{3})\s*\|[^|]*\|[^|]*\|[^|]*\|[^|]*\|\s*([^|]+?)\s*\|/);
      if (!match) {
        continue;
      }

      const id = Number(match[1]);
      const fileColumn = match[2].trim();
      const whereRefs = [...fileColumn.matchAll(/`([^`]+)`/g)].map((token) => token[1].trim());

      tasks.push({
        id,
        title: `TODO-${String(id).padStart(3, '0')}`,
        whereRefs,
      });
      continue;
    }

    const columns = trimmed
      .split('|')
      .map((column) => column.trim())
      .filter((column, index, arr) => !(index === 0 && column === '') && !(index === arr.length - 1 && column === ''));

    if (columns.length < 3) {
      continue;
    }

    const idMatch = columns[0].match(idRegex);
    if (idMatch) {
      const id = Number(idMatch[2]);
      const todoColumn = columns[2];
      const whereRefs = [...todoColumn.matchAll(/`([^`]+)`/g)].map((token) => token[1].trim());

      tasks.push({
        id,
        title: `${idMatch[1]}-${idMatch[2]}`,
        whereRefs,
      });
      continue;
    }

    // Current kernel plan table uses IDs like AEP-P1-006, AGENT-P2-006, YAPPC-P1-001.
    const modernId = columns[0];
    if (!modernIdRegex.test(modernId) || modernId === 'ID') {
      continue;
    }

    const whereColumn = columns[2] ?? '';
    const whereRefs = [...whereColumn.matchAll(/`([^`]+)`/g)].map((token) => token[1].trim());
    tasks.push({
      id: tasks.length + 1,
      title: modernId,
      whereRefs,
    });
  }

  return tasks;
}

function isWorkspacePathRef(ref) {
  const normalized = normalizePathRef(ref);
  const pathLikeRoots = ['products/', 'platform/', 'platform-kernel/', 'platform-plugins/', 'shared-services/', 'scripts/', 'config/', 'docs/', '.kernel/'];
  const hasKnownRoot = pathLikeRoots.some((root) => normalized.startsWith(root));
  const hasKnownExtension = /\.(json|mjs|yml|yaml|java|kt|kts|ts|tsx|js|jsx|md)$/i.test(normalized);
  return hasKnownRoot || hasKnownExtension;
}

function normalizePathRef(ref) {
  return ref.replace(/^\.\//, '').replace(/\\/g, '/').trim();
}

function fileExistsFromRepo(ref) {
  const normalized = normalizePathRef(ref);
  if (normalized.endsWith('/*')) {
    const directory = normalized.slice(0, -2);
    const absoluteDirectory = path.join(repoRoot, directory);
    return existsSync(absoluteDirectory) && readdirSync(absoluteDirectory).length > 0;
  }
  return existsSync(path.join(repoRoot, normalized));
}

function classifyExecutionWave(taskId) {
  if (taskId <= 10) {
    return 'wave-1-foundation';
  }
  if (taskId <= 23) {
    return 'wave-2-runtime-governance';
  }
  if (taskId <= 35) {
    return 'wave-3-quality-performance';
  }
  return 'wave-4-release-ops';
}

function nextActionForMaturity(task) {
  if (task.status === 'Deferred') {
    return 'Track explicit defer reason and keep evidence current until execution resumes.';
  }
  if (task.status === 'Blocked') {
    return 'Unblock owner and update task override with blocker evidence before release gating.';
  }
  if (task.maturityDepth <= 2) {
    return 'Upgrade from posture/static checks to behavioral tests with executable failure scenarios.';
  }
  if (task.maturityDepth === 3) {
    return 'Add release-grade evidence linkage and strict pass/fail coverage thresholds.';
  }
  if (task.maturityDepth === 4) {
    return 'Add production-like runtime evidence to reach maturity depth 5.';
  }
  return 'Maintain depth 5 with freshness checks and regression protection.';
}

function buildIncrementalRoadmap(resolvedTasks) {
  const remaining = resolvedTasks
    .filter((task) => task.status !== 'Complete')
    .map((task) => ({
      id: task.id,
      title: task.title,
      status: task.status,
      maturityDepth: task.maturityDepth,
      targetDepth: 5,
      depthGap: Math.max(0, 5 - task.maturityDepth),
      ownerTeam: task.ownerTeam,
      ownerModule: task.ownerModule,
      requiredForRelease: task.requiredForRelease === true,
      executionWave: classifyExecutionWave(task.id),
      nextAction: nextActionForMaturity(task),
      deferred: task.status === 'Deferred',
      deferReason: task.deferReason ?? null,
    }))
    .sort((left, right) => {
      if (left.requiredForRelease !== right.requiredForRelease) {
        return left.requiredForRelease ? -1 : 1;
      }
      if (left.depthGap !== right.depthGap) {
        return right.depthGap - left.depthGap;
      }
      return left.id - right.id;
    });

  const byWave = remaining.reduce((acc, task) => {
    const key = task.executionWave;
    if (!acc[key]) {
      acc[key] = [];
    }
    acc[key].push(task.id);
    return acc;
  }, {});

  const deferredTaskIds = remaining.filter((task) => task.deferred).map((task) => task.id);

  return {
    generatedAt: new Date().toISOString(),
    targetMaturityDepth: 5,
    remainingTaskCount: remaining.length,
    deferredTaskIds,
    waveBuckets: byWave,
    tasks: remaining,
  };
}

function writeJsonAtomicWithRetry(filePath, content, maxAttempts = 5) {
  let lastError;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    const tempPath = `${filePath}.tmp-${process.pid}-${Date.now()}-${attempt}`;
    try {
      writeFileSync(tempPath, content, 'utf8');
      renameSync(tempPath, filePath);
      return;
    } catch (error) {
      lastError = error;
      try {
        rmSync(tempPath, { force: true });
      } catch {
        // Best-effort cleanup only.
      }

      const retryable =
        error
        && typeof error === 'object'
        && 'code' in error
        && RETRYABLE_WRITE_CODES.has(error.code);
      if (!retryable || attempt === maxAttempts) {
        throw error;
      }
    }
  }

  if (lastError) {
    throw lastError;
  }
}

export function runTaskMatrixCheck({ writeEvidence = true } = {}) {
  const violations = [];
  const warnings = [];

  if (!existsSync(configPath)) {
    violations.push('Missing config/kernel-implementation-task-matrix.json');
    return { violations, warnings, report: null };
  }

  const matrixConfig = readJson(configPath);
  const planPath = path.join(repoRoot, matrixConfig.planPath);
  if (!existsSync(planPath)) {
    violations.push(`Missing implementation plan file ${matrixConfig.planPath}`);
    return { violations, warnings, report: null };
  }

  const planSource = readFileSync(planPath, 'utf8');
  const planTasks = parsePlanTasks(planSource);

  if (planTasks.length !== matrixConfig.requiredTaskCount) {
    violations.push(`Expected ${matrixConfig.requiredTaskCount} plan tasks, found ${planTasks.length}`);
  }

  const allowedStatus = new Set(matrixConfig.statusPolicy?.allowed ?? []);
  const resolvedTasks = [];

  for (const task of planTasks) {
    const override = matrixConfig.overrides?.[String(task.id)] ?? {};
    const resolved = {
      ...matrixConfig.defaults,
      ...override,
      id: task.id,
      title: task.title,
      whereRefs: task.whereRefs,
    };

    if (typeof resolved.ownerTeam !== 'string' || resolved.ownerTeam.length === 0) {
      violations.push(`Task ${task.id} is missing ownerTeam`);
    }
    if (typeof resolved.ownerModule !== 'string' || resolved.ownerModule.length === 0) {
      violations.push(`Task ${task.id} is missing ownerModule`);
    }
    if (!allowedStatus.has(resolved.status)) {
      violations.push(`Task ${task.id} has invalid status ${JSON.stringify(resolved.status)}`);
    }
    if (!Number.isInteger(resolved.maturityDepth) || resolved.maturityDepth < 1 || resolved.maturityDepth > 5) {
      violations.push(`Task ${task.id} has invalid maturityDepth ${JSON.stringify(resolved.maturityDepth)}`);
    }

    if (resolved.status === 'Deferred' && matrixConfig.statusPolicy?.deferredRequiresReason === true) {
      if (typeof resolved.deferReason !== 'string' || resolved.deferReason.trim().length === 0) {
        violations.push(`Task ${task.id} is Deferred but missing deferReason`);
      }
    }

    if ((!Array.isArray(resolved.whereRefs) || resolved.whereRefs.length === 0) && !resolved.title.startsWith('FND-')) {
      warnings.push(`Task ${task.id} has no parsed Where references in implementation plan`);
    }

    const pathRefs = (resolved.whereRefs ?? []).filter(isWorkspacePathRef);
    for (const ref of pathRefs) {
      if (!fileExistsFromRepo(ref)) {
        warnings.push(`Task ${task.id} references non-existing path ${JSON.stringify(ref)}`);
      }
    }

    if (!Array.isArray(resolved.evidenceRefs)) {
      violations.push(`Task ${task.id} evidenceRefs must be an array`);
    }

    if (resolved.status === 'Complete') {
      if (!Array.isArray(resolved.evidenceRefs) || resolved.evidenceRefs.length === 0) {
        violations.push(`Task ${task.id} is Complete but has no evidenceRefs`);
      } else {
        for (const evidenceRef of resolved.evidenceRefs) {
          if (!fileExistsFromRepo(evidenceRef)) {
            violations.push(`Task ${task.id} evidenceRef does not exist: ${JSON.stringify(evidenceRef)}`);
          }
        }
      }
    }

    if (resolved.requiredForRelease === true && resolved.status === 'Blocked') {
      violations.push(`Task ${task.id} is requiredForRelease but Blocked`);
    }

    resolvedTasks.push(resolved);
  }

  const byStatus = resolvedTasks.reduce((acc, task) => {
    acc[task.status] = (acc[task.status] ?? 0) + 1;
    return acc;
  }, {});

  const averageMaturityDepth = Number(
    (
      resolvedTasks.reduce((sum, task) => sum + task.maturityDepth, 0)
      / Math.max(1, resolvedTasks.length)
    ).toFixed(2),
  );

  const report = {
    generatedAt: new Date().toISOString(),
    status: violations.length === 0 ? 'passed' : 'failed',
    summary: {
      requiredTaskCount: matrixConfig.requiredTaskCount,
      parsedTaskCount: planTasks.length,
      statusCounts: byStatus,
      averageMaturityDepth,
      warningCount: warnings.length,
      violationCount: violations.length,
    },
    incrementalRoadmap: buildIncrementalRoadmap(resolvedTasks),
    tasks: resolvedTasks,
    warnings,
    violations,
  };

  if (writeEvidence) {
    mkdirSync(evidenceDir, { recursive: true });
    writeJsonAtomicWithRetry(evidencePath, `${JSON.stringify(report, null, 2)}\n`);
  }

  return { violations, warnings, report };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = runTaskMatrixCheck();

  if (result.violations.length > 0) {
    console.error('Kernel implementation task matrix check failed:\n');
    for (const violation of result.violations) {
      console.error(`- ${violation}`);
    }
    if (result.warnings.length > 0) {
      console.error('\nWarnings:');
      for (const warning of result.warnings) {
        console.error(`- ${warning}`);
      }
    }
    process.exit(1);
  }

  if (result.warnings.length > 0) {
    console.warn('Kernel implementation task matrix check passed with warnings:');
    for (const warning of result.warnings) {
      console.warn(`- ${warning}`);
    }
  }

  console.log(`Kernel implementation task matrix check passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
}
