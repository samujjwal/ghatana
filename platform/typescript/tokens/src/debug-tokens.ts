import { tokens } from './registry';

function isObjectLike(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function findFunctions(obj: unknown, path = ''): string[] {
  const results: string[] = [];

  if (!isObjectLike(obj)) {
    return results;
  }

  for (const [key, value] of Object.entries(obj)) {
    const currentPath = path ? `${path}.${key}` : key;
    if (typeof value === 'function') {
      results.push(currentPath);
    } else if (isObjectLike(value)) {
      results.push(...findFunctions(value, currentPath));
    }
  }

  return results;
}

// Only run in development
if (process.env.NODE_ENV !== 'production') {
  const functions = findFunctions(tokens);
  if (functions.length > 0) {
    console.log('Functions found in tokens:');
    functions.forEach(f => console.log(`  - ${f}`));
  }
}
