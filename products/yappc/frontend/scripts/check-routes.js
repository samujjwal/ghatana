const fs = require('fs');
const path = require('path');

const routesPath = path.resolve(
  __dirname,
  '..',
  'apps',
  'web',
  'src',
  'routes.ts'
);
const docPath = path.resolve(
  __dirname,
  '..',
  'docs',
  'ui-flow-react-router-framework.md'
);

const routesExists = fs.existsSync(routesPath);
const docsExists = fs.existsSync(docPath);

if (!routesExists && !docsExists) {
  console.error(
    `Neither ${routesPath} nor ${docPath} exist. Add a routes file or update the docs.`
  );
  process.exit(1);
}

if (docsExists) {
  const content = fs.readFileSync(docPath, 'utf8');
  if (
    !content.includes('createBrowserRouter') &&
    !content.includes('/app/w/:workspaceId/p/:projectId')
  ) {
    console.error('Docs file does not include expected route markers.');
    process.exit(2);
  }
}

console.log('Route smoke check passed.');
process.exit(0);
