import { fileURLToPath } from 'url';
import { dirname } from 'path';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

try {
  console.log('Running test with Node.js version:', process.version);
  console.log('Current directory:', process.cwd());
  
  // Run the test using vitest
  const _result = execSync('pnpm vitest run test/smoke.test.ts --run', { 
    cwd: __dirname,
    stdio: 'inherit',
    env: { ...process.env, NODE_DEBUG: '*' }
  });
  
  console.log('Test completed successfully');
  process.exit(0);
} catch (error) {
  console.error('Test failed:', error);
  process.exit(1);
}
