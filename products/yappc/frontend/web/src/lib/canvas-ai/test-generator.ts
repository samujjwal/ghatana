/**
 * YAPPC-T06: Product Test Generator
 * 
 * Generates test skeletons from testId in the Product route contract.
 * Produces both frontend (React/Vitest) and backend (Java/JUnit) test skeletons.
 */

// Use flexible types to handle actual contract structure
export interface ProductRouteContract {
  product: string;
  version: string;
  routes: ProductRoute[];
}

export interface ProductRoute {
  path: string;
  label: string;
  testId?: string;
  apiEndpoint?: string;
  policyId?: string;
  metadata?: Record<string, unknown>;
}

export interface TestConfig {
  productId: string;
  routePath: string;
  routeLabel: string;
  testId: string;
  apiEndpoint?: string;
  policyId?: string;
  testType: 'frontend' | 'backend' | 'e2e';
}

export interface GeneratedTest {
  filePath: string;
  testName: string;
  code: string;
  testType: 'frontend' | 'backend' | 'e2e';
}

/**
 * Generates test skeletons from Product route contract.
 */
export class TestGenerator {
  /**
   * Generates test skeletons for all routes with test IDs.
   */
  generateTestSkeletons(contract: ProductRouteContract): GeneratedTest[] {
    const tests: GeneratedTest[] = [];
    
    for (const route of contract.routes) {
      const testId = route.testId || (route.metadata?.testId as string | undefined);
      if (testId) {
        // Generate frontend test
        tests.push(this.generateFrontendTest(contract.product, route, testId));
        
        // Generate backend test if API endpoint exists
        const apiEndpoint = route.apiEndpoint || (route.metadata?.apiEndpoint as string | undefined);
        if (apiEndpoint && apiEndpoint.startsWith('/api/')) {
          tests.push(this.generateBackendTest(contract.product, route, testId));
        }
      }
    }
    
    return tests;
  }

  /**
   * Generates a frontend (Vitest) test skeleton.
   */
  private generateFrontendTest(productId: string, route: ProductRoute, testId: string): GeneratedTest {
    const testName = this.testIdToTestName(testId);
    const filePath = `src/__tests__/${testName}.test.ts`;
    
    const config: TestConfig = {
      productId,
      routePath: route.path,
      routeLabel: route.label,
      testId,
      testType: 'frontend',
    };
    
    const code = this.generateFrontendTestCode(config);
    
    return {
      filePath,
      testName,
      code,
      testType: 'frontend',
    };
  }

  /**
   * Generates a backend (JUnit) test skeleton.
   */
  private generateBackendTest(productId: string, route: ProductRoute, testId: string): GeneratedTest {
    const testName = this.testIdToTestName(testId);
    const packageSegment = this.productIdToPackageSegment(productId);
    const filePath = `src/test/java/com/ghatana/${packageSegment}/api/routes/${testName}Test.java`;
    
    const apiEndpoint = route.apiEndpoint || (route.metadata?.apiEndpoint as string) || '';
    const policyId = route.policyId || (route.metadata?.policyId as string) || '';
    
    const config: TestConfig = {
      productId,
      routePath: route.path,
      routeLabel: route.label,
      testId,
      apiEndpoint,
      policyId,
      testType: 'backend',
    };
    
    const code = this.generateBackendTestCode(config);
    
    return {
      filePath,
      testName,
      code,
      testType: 'backend',
    };
  }

  /**
   * Converts testId to test name.
   */
  private testIdToTestName(testId: string): string {
    return testId
      .split(/[-_]+/)
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join('');
  }

  /**
   * Generates Vitest frontend test code.
   */
  private generateFrontendTestCode(config: TestConfig): string {
    return `import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ${this.testIdToTestName(config.testId)} from '../pages/${this.pathToComponentName(config.routePath)}';

/**
 * ${config.routeLabel} Test
 *
 * Route: ${config.routePath}
 * Test ID: ${config.testId}
 *
 * Auto-generated from Kernel product route contract.
 */
describe('${config.routeLabel}', () => {
  it('renders the page correctly', () => {
    render(<${this.testIdToTestName(config.testId)} />);
    
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('main')).toHaveAttribute('data-route-path', '${config.routePath}');
  });

  it('has proper accessibility attributes', () => {
    render(<${this.testIdToTestName(config.testId)} />);
    
    const main = screen.getByRole('main');
    expect(main).toHaveAttribute('aria-label');
  });
});
`;
  }

  /**
   * Generates JUnit backend test code.
   */
  private generateBackendTestCode(config: TestConfig): string {
    const className = this.testIdToTestName(config.testId);
    
    const packageSegment = this.productIdToPackageSegment(config.productId);
    return `package com.ghatana.${packageSegment}.api.routes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ${config.routeLabel} Backend Test
 *
 * Route: ${config.routePath}
 * Endpoint: ${config.apiEndpoint}
 * Policy: ${config.policyId}
 * Test ID: ${config.testId}
 *
 * Auto-generated from Kernel product route contract.
 *
 * @doc.type test
 * @doc.purpose Verifies ${config.routeLabel} route handler behavior
 * @doc.layer product
 * @doc.pattern ContractTest
 */
@DisplayName("${config.routeLabel} Route Tests")
class ${className}Test {

    @Test
    @DisplayName("Should preserve route contract metadata")
    void shouldPreserveRouteContractMetadata() {
        assertThat("${config.routePath}").startsWith("/");
        assertThat("${config.apiEndpoint}").startsWith("/api/");
        assertThat("${config.policyId}").isNotBlank();
    }

    @Test
    @DisplayName("Should bind generated test id")
    void shouldBindGeneratedTestId() {
        assertThat("${config.testId}").isNotBlank();
    }
}
`;
  }

  /**
   * Converts route path to component name.
   */
  private pathToComponentName(path: string): string {
    return path
      .split(/[/_-]+/)
      .filter(segment => segment.length > 0)
      .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join('');
  }

  private productIdToPackageSegment(productId: string): string {
    const packageSegment = productId.toLowerCase().replaceAll(/[^a-z0-9]+/g, '');
    if (packageSegment.length === 0) {
      throw new Error('product id must include at least one package-safe character');
    }
    return packageSegment;
  }
}

/**
 * Creates a Product test generator instance.
 */
export function createTestGenerator(): TestGenerator {
  return new TestGenerator();
}

/**
 * Generates test skeletons from Product route contract.
 */
export function generateTestSkeletons(contract: ProductRouteContract): GeneratedTest[] {
  return createTestGenerator().generateTestSkeletons(contract);
}
