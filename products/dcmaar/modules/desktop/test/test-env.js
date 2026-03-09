console.log('Node.js version:', process.version);
console.log('Current directory:', process.cwd());
try {
  const fs = require('fs');
  console.log('Test file exists:', fs.existsSync('./test/smoke.test.ts'));
  console.log('Environment:');
  console.log('- NODE_ENV:', process.env.NODE_ENV);
  console.log('- CI:', process.env.CI);
  console.log('Test completed successfully');
  process.exit(0);
} catch (error) {
  console.error('Test failed:', error);
  process.exit(1);
}
