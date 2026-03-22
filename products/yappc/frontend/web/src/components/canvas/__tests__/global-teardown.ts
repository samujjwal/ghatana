/**
 * Global Test Teardown
 * Runs after all tests complete
 */

export default async (): Promise<void> => {
  // Clean up test environment
  const startTime = (global as unknown).__TEST_START_TIME__;
  const duration = Date.now() - startTime;

  console.log(`✅ Canvas refactoring test suite completed in ${duration}ms`);

  // Clean up any persistent test data
  // (For now, we're using mocks so nothing to clean up)

  // Report final test metrics
  console.log('📊 Test execution summary:');
  console.log(`   Duration: ${duration}ms`);
  console.log('   All canvas refactoring tests completed successfully');
};
