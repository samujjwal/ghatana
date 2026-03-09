#!/usr/bin/env node
/* eslint-disable @typescript-eslint/no-require-imports, no-console, @typescript-eslint/no-unused-vars */

/**
 * Canvas Refactoring Test Runner
 * Comprehensive test execution for all phases
 */

const { execSync } = require('child_process');
const path = require('path');

console.log('🎯 Canvas Refactoring Test Suite');
console.log('================================');

const testSuites = [
  {
    name: 'Unit Tests',
    command: 'npm run test:unit:canvas',
    description: 'Core component unit tests',
  },
  {
    name: 'Integration Tests',
    command: 'npm run test:integration:canvas',
    description: 'Cross-component integration tests',
  },
  {
    name: 'Performance Tests',
    command: 'npm run test:performance:canvas',
    description: 'Performance and stress tests',
  },
  {
    name: 'End-to-End Tests',
    command: 'npm run test:e2e:canvas',
    description: 'Full user flow validation',
  },
];

async function runTestSuite(suite) {
  console.log(`\n🧪 Running ${suite.name}...`);
  console.log(`   ${suite.description}`);

  try {
    const startTime = Date.now();
    execSync(suite.command, {
      stdio: 'inherit',
      cwd: process.cwd(),
    });
    const duration = Date.now() - startTime;

    console.log(`✅ ${suite.name} completed in ${duration}ms`);
    return { success: true, duration };
  } catch (error) {
    console.log(`❌ ${suite.name} failed`);
    console.error(error.message);
    return { success: false, duration: 0 };
  }
}

async function runAllTests() {
  const results = [];
  let totalDuration = 0;

  console.log('\n🚀 Starting comprehensive canvas test execution...\n');

  for (const suite of testSuites) {
    const result = await runTestSuite(suite);
    results.push({ ...suite, ...result });
    totalDuration += result.duration;
  }

  // Summary
  console.log('\n📊 Test Execution Summary');
  console.log('=========================');

  const passed = results.filter((r) => r.success).length;
  const failed = results.filter((r) => !r.success).length;

  console.log(`Total test suites: ${results.length}`);
  console.log(`Passed: ${passed}`);
  console.log(`Failed: ${failed}`);
  console.log(`Total duration: ${totalDuration}ms`);

  results.forEach((result) => {
    const status = result.success ? '✅' : '❌';
    console.log(`${status} ${result.name}: ${result.duration}ms`);
  });

  if (failed > 0) {
    console.log(
      '\n⚠️  Some test suites failed. Please check the output above.'
    );
    process.exit(1);
  } else {
    console.log('\n🎉 All canvas refactoring tests passed successfully!');
    console.log('\nPhase 1: Generic Canvas Foundation ✅');
    console.log('Phase 2: Registry Migration ✅');
    console.log('Phase 3: Performance & Advanced Features ✅');
    console.log(
      '\nCanvas refactoring project is fully validated and ready for production! 🚀'
    );
  }
}

// Handle command line arguments
const args = process.argv.slice(2);

if (args.includes('--help') || args.includes('-h')) {
  console.log('\nUsage: node test-runner.js [options]');
  console.log('\nOptions:');
  console.log('  --unit          Run only unit tests');
  console.log('  --integration   Run only integration tests');
  console.log('  --performance   Run only performance tests');
  console.log('  --e2e          Run only end-to-end tests');
  console.log('  --coverage     Run with coverage reporting');
  console.log('  --watch        Run in watch mode');
  console.log('  --help, -h     Show this help message');
  process.exit(0);
}

// Filter test suites based on arguments
let suitesToRun = testSuites;

if (args.includes('--unit')) {
  suitesToRun = testSuites.filter((s) => s.name === 'Unit Tests');
} else if (args.includes('--integration')) {
  suitesToRun = testSuites.filter((s) => s.name === 'Integration Tests');
} else if (args.includes('--performance')) {
  suitesToRun = testSuites.filter((s) => s.name === 'Performance Tests');
} else if (args.includes('--e2e')) {
  suitesToRun = testSuites.filter((s) => s.name === 'End-to-End Tests');
}

// Update test commands if coverage requested
if (args.includes('--coverage')) {
  suitesToRun = suitesToRun.map((suite) => ({
    ...suite,
    command: `${suite.command} --coverage`,
  }));
}

// Update test commands if watch mode requested
if (args.includes('--watch')) {
  suitesToRun = suitesToRun.map((suite) => ({
    ...suite,
    command: `${suite.command} --watch`,
  }));
}

// Run the tests
runAllTests().catch((error) => {
  console.error('❌ Test runner encountered an error:', error);
  process.exit(1);
});
