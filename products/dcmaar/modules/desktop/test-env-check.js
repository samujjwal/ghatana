// Simple script to check the environment
console.log('Node.js version:', process.version);
console.log('Current directory:', process.cwd());
console.log('Environment:');
console.log('- NODE_ENV:', process.env.NODE_ENV);
console.log('- CI:', process.env.CI);

// Check if we can import the test file
try {
  const fs = require('fs');
  console.log('Test file exists:', fs.existsSync('./test/smoke.test.ts'));
  
  // Try to read the test file
  const testContent = fs.readFileSync('./test/smoke.test.ts', 'utf-8');
  console.log('Test file content length:', testContent.length);
  
  console.log('Environment check completed successfully');
  process.exit(0);
} catch (error) {
  console.error('Environment check failed:', error);
  process.exit(1);
}
