#!/usr/bin/env node

/**
 * Canvas feature story generator.
 *
 * Reads the canonical Markdown source (`docs/canvas-feature-stories.md`) and
 * emits a strongly-typed TypeScript data module under
 * `libs/canvas/src/features/stories/canvas-feature-stories.generated.ts`.
 *
 * The output is consumed by UI components and tests to ensure a single source
 * of truth for blueprint-linked user stories.
 */

const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..');
const SOURCE_PATH = path.join(ROOT, 'docs', 'canvas-feature-stories.md');
const TARGET_DIR = path.join(ROOT, 'libs', 'canvas', 'src', 'features', 'stories');
const TARGET_FILE = path.join(TARGET_DIR, 'canvas-feature-stories.generated.ts');

const slugify = (value) =>
  value
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

const readSource = () => {
  if (!fs.existsSync(SOURCE_PATH)) {
    throw new Error(`Source Markdown not found at ${SOURCE_PATH}`);
  }
  return fs.readFileSync(SOURCE_PATH, 'utf8');
};

const ensureTargetDirectory = () => {
  fs.mkdirSync(TARGET_DIR, { recursive: true });
};

const parseMarkdown = (markdown) => {
  const lines = markdown.split(/\r?\n/);

  /** @type {import('../libs/canvas/src/features/stories/types').CanvasFeatureStoryCategory[]} */
  const categories = [];
  let currentCategory = null;
  let currentStory = null;
  let mode = null;
  let storyOrder = 0;

  const flushStory = () => {
    if (currentStory && currentCategory) {
      currentStory.acceptanceCriteria = currentStory.acceptanceCriteria || [];
      currentStory.tests = currentStory.tests || [];
      if (!currentStory.progress) {
        currentStory.progress = {
          status: 'Not Started',
          summary: 'Status not documented in docs/canvas-feature-stories.md yet.',
          raw: 'Generated progress placeholder — update docs/canvas-feature-stories.md.',
        };
      }
      if (currentStory.tests.length === 0) {
        currentStory.tests.push({
          id: `TEST-${currentStory.id}-PENDING`,
          type: 'Todo',
          summary: 'Tests pending - update docs/canvas-feature-stories.md with validation coverage.',
          targets: ['docs/canvas-feature-stories.md'],
          raw: 'Placeholder generated: Tests pending - update docs/canvas-feature-stories.md.',
        });
      }
      currentCategory.stories.push(currentStory);
      currentStory = null;
    }
    mode = null;
  };

  const flushCategory = () => {
    if (currentStory) {
      flushStory();
    }
    currentCategory = null;
  };

  const parseCategory = (line, order) => {
    const match = line.match(/^##\s+([\dA-Za-z]+)\.\s+(.*?)\s*(\((.*?)\))?\s*$/);
    if (!match) {
      return null;
    }
    const [, id, title, , blueprintClause] = match;
    return {
      id: id.trim(),
      title: title.trim(),
      blueprintReference: blueprintClause?.trim() || undefined,
      order,
      stories: [],
    };
  };

  const parseStoryHeading = (line) => {
    const match = line.match(/^###\s+([\dA-Za-z\.\-]+)\s+(.*)$/);
    if (!match) {
      return null;
    }
    const [, id, title] = match;
    return { id: id.trim(), title: title.trim() };
  };

const parseNarrative = (line) => {
  const match = line.match(/^\*\*Story\*\*:\s*(.*)$/);
  return match ? match[1].trim() : null;
};

const parseProgress = (line) => {
  const match = line.match(/^\*\*Progress\*\*\s*:?\s*(.*)$/i);
  if (!match) {
    return null;
  }
  const content = match[1].trim();
  if (!content) {
    return {
      status: 'Unknown',
      summary: undefined,
      raw: line.trim(),
    };
  }
  const separatorMatch = content.match(/\s[-–—]\s/);
  if (separatorMatch) {
    const index = separatorMatch.index ?? -1;
    if (index >= 0) {
      const status = content.slice(0, index).trim();
      const summary = content.slice(index + separatorMatch[0].length).trim();
      return {
        status: status || 'Unknown',
        summary: summary || undefined,
        raw: line.trim(),
      };
    }
  }
  return {
    status: content,
    summary: undefined,
    raw: line.trim(),
  };
};

const parseAcceptanceCriterion = (line, index, storyId) => {
  const bulletMatch = line.match(/^-+\s*(.*)$/);
  if (!bulletMatch) {
    return null;
  }
    const rawContent = bulletMatch[1].trim();
    const labelMatch = rawContent.match(/^\*\*(.+?)\*\*\s*(.*)$/);
    const title = labelMatch ? labelMatch[1].trim() : undefined;
    const summary = labelMatch ? labelMatch[2].trim() : rawContent;
    const id = `AC-${storyId}-${index + 1}`;
    return {
      id,
      title,
      summary,
      raw: line.trim(),
    };
  };

  const parseTestReference = (line, index, storyId) => {
    const bulletMatch = line.match(/^-+\s*(.*)$/);
    if (!bulletMatch) {
      return null;
    }
    const rawContent = bulletMatch[1].trim();
    const labelMatch = rawContent.match(/^\*\*(.+?)\*\*\s*(.*)$/);
    const type = labelMatch ? labelMatch[1].trim() : 'General';
    const rest = labelMatch ? labelMatch[2].trim() : rawContent;
    const targets = [];
    const codeRegex = /`([^`]+)`/g;
    let codeMatch;
    while ((codeMatch = codeRegex.exec(rest)) !== null) {
      targets.push(codeMatch[1]);
    }
    const summary = rest.replace(/`([^`]+)`/g, (_, value) => value).trim();
    const id = `TEST-${storyId}-${index + 1}`;
    return {
      id,
      type,
      summary,
      targets,
      raw: line.trim(),
    };
  };

  lines.forEach((rawLine) => {
    const line = rawLine.trimEnd();
    if (!line || line.startsWith('---')) {
      return;
    }

    if (line.startsWith('# ')) {
      // Ignore top-level title
      return;
    }

    if (line.startsWith('## ')) {
      const nextCategoryOrder = categories.length;
      const parsedCategory = parseCategory(line, nextCategoryOrder);
      if (parsedCategory) {
        flushCategory();
        currentCategory = parsedCategory;
        categories.push(currentCategory);
        storyOrder = 0;
      }
      return;
    }

    if (line.startsWith('### ')) {
      if (!currentCategory) {
        throw new Error(`Encountered story without category context: ${line}`);
      }
      flushStory();
      const heading = parseStoryHeading(line);
      if (!heading) {
        throw new Error(`Unable to parse story heading: ${line}`);
      }
      const normalizedId = heading.id.replace(/[^A-Za-z0-9]+/g, '-').toLowerCase();
      currentStory = {
        id: heading.id,
        slug: `${normalizedId}-${slugify(heading.title)}`,
        title: heading.title,
        order: storyOrder++,
        narrative: '',
        categoryId: currentCategory.id,
        categoryTitle: currentCategory.title,
        blueprintReference: currentCategory.blueprintReference,
        acceptanceCriteria: [],
        tests: [],
        raw: [line],
      };
      mode = null;
      return;
    }

    if (currentStory) {
      currentStory.raw.push(line);
      const narrative = parseNarrative(line);
      if (narrative) {
        currentStory.narrative = narrative;
        return;
      }

      const progress = parseProgress(line);
      if (progress) {
        currentStory.progress = progress;
        return;
      }

      if (line.startsWith('**Acceptance Criteria**')) {
        mode = 'criteria';
        return;
      }
      if (line.startsWith('**Tests**')) {
        mode = 'tests';
        return;
      }

      if (mode === 'criteria' && line.trimStart().startsWith('-')) {
        const criterion =
          parseAcceptanceCriterion(line, currentStory.acceptanceCriteria.length, currentStory.id);
        if (criterion) {
          currentStory.acceptanceCriteria.push(criterion);
        }
        return;
      }

      if (mode === 'tests' && line.trimStart().startsWith('-')) {
        const test =
          parseTestReference(line, currentStory.tests.length, currentStory.id);
        if (test) {
          currentStory.tests.push(test);
        }
        return;
      }
    }
  });

  flushStory();
  flushCategory();

  return categories;
};

const serialize = (categories) => {
  const storyIds = new Set();
  const slugSet = new Set();

  for (const category of categories) {
    for (const story of category.stories) {
      if (storyIds.has(story.id)) {
        throw new Error(`Duplicate story id detected: ${story.id}`);
      }
      if (slugSet.has(story.slug)) {
        throw new Error(`Duplicate story slug detected: ${story.slug}`);
      }
      storyIds.add(story.id);
      slugSet.add(story.slug);
    }
  }

  const storyCount = [...storyIds].length;
  const content = JSON.stringify(categories, null, 2);

  const header = `/* eslint-disable */\n// Auto-generated file. Do not edit manually.\n// Run \`node scripts/generate-canvas-feature-stories.js\` after updating docs/canvas-feature-stories.md.\n\nimport type { CanvasFeatureStoryCategory } from './types';\n\n`;
  const body = `export const canvasFeatureStoryCategories: CanvasFeatureStoryCategory[] = ${content} as const;\nexport const canvasFeatureStoryCount = ${storyCount} as const;\n`;
  return header + body;
};

const main = () => {
  const markdown = readSource();
  const categories = parseMarkdown(markdown);
  ensureTargetDirectory();
  const output = serialize(categories);
  fs.writeFileSync(TARGET_FILE, output, 'utf8');
  const storyCount = categories.reduce((total, category) => total + category.stories.length, 0);
  console.log(
    `Generated ${TARGET_FILE.replace(`${ROOT}${path.sep}`, '')} with ${categories.length} categories and ${storyCount} stories.`,
  );
};

if (require.main === module) {
  try {
    main();
  } catch (error) {
    console.error('[canvas-stories] Generation failed:', error instanceof Error ? error.message : error);
    process.exit(1);
  }
}
