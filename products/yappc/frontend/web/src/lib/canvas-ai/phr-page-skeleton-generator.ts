/**
 * YAPPC-T03: PHR Page Skeleton Generator
 * 
 * Generates PHR page skeletons from canonical route contract.
 * Produces React component code with proper structure, i18n, a11y, and metadata.
 */

// Use flexible types to handle actual contract structure
export interface PhrRouteContract {
  product: string;
  version: string;
  roleOrder?: Record<string, number>;
  routes: PhrRoute[];
}

export interface PhrRoute {
  path: string;
  label: string;
  description?: string;
  group?: string;
  stability?: string;
  minimumRole?: string;
  surface?: string[];
  i18nKey?: string;
  descriptionI18nKey?: string;
  accessibility?: Record<string, boolean>;
  metadata?: Record<string, unknown>;
}

export interface PageSkeletonConfig {
  routePath: string;
  routeLabel: string;
  routeDescription: string;
  routeGroup: string;
  minimumRole: string;
  i18nKey: string;
  descriptionI18nKey: string;
  accessibility?: Record<string, boolean>;
  surface: string[];
  metadata?: Record<string, unknown>;
}

export interface GeneratedPageSkeleton {
  filePath: string;
  componentName: string;
  code: string;
  imports: string[];
  dependencies: string[];
}

/**
 * Generates React page skeleton code from route contract data.
 */
export class PhrPageSkeletonGenerator {
  /**
   * Generates page skeletons for all routes in the contract.
   */
  generatePageSkeletons(contract: PhrRouteContract): GeneratedPageSkeleton[] {
    return contract.routes
      .filter(route => this.shouldGeneratePage(route))
      .map(route => this.generatePageSkeleton(route));
  }

  /**
   * Determines if a page should be generated for the route.
   */
  private shouldGeneratePage(route: PhrRoute): boolean {
    // Only generate for web surface routes
    const surface = route.surface || [];
    if (!surface.includes('web') && !route.path.startsWith('/mobile/')) {
      return false;
    }

    // Skip blocked or hidden routes
    if (route.stability === 'blocked' || route.stability === 'hidden') {
      return false;
    }

    return true;
  }

  /**
   * Generates a single page skeleton.
   */
  private generatePageSkeleton(route: PhrRoute): GeneratedPageSkeleton {
    const config: PageSkeletonConfig = {
      routePath: route.path,
      routeLabel: route.label,
      routeDescription: route.description || '',
      routeGroup: route.group || 'default',
      minimumRole: route.minimumRole || 'viewer',
      i18nKey: route.i18nKey || `phr.routes.${this.pathToKey(route.path)}.label`,
      descriptionI18nKey: route.descriptionI18nKey || `phr.routes.${this.pathToKey(route.path)}.description`,
      accessibility: route.accessibility,
      surface: route.surface || ['web'],
      metadata: route.metadata,
    };

    const componentName = this.pathToComponentName(route.path);
    const filePath = this.pathToFilePath(route.path);
    const code = this.generateComponentCode(config, componentName);
    const imports = this.extractImports(config);
    const dependencies = this.extractDependencies(config);

    return {
      filePath,
      componentName,
      code,
      imports,
      dependencies,
    };
  }

  /**
   * Converts route path to component name.
   */
  private pathToComponentName(path: string): string {
    return path
      .split('/')
      .filter(segment => segment.length > 0)
      .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join('');
  }

  /**
   * Converts route path to file path.
   */
  private pathToFilePath(path: string): string {
    const segments = path.split('/').filter(segment => segment.length > 0);
    if (segments.length === 0) {
      return 'src/pages/HomePage.tsx';
    }
    const fileName = this.pathToComponentName(path);
    return `src/pages/${fileName}.tsx`;
  }

  /**
   * Converts route path to i18n key.
   */
  private pathToKey(path: string): string {
    return path
      .split('/')
      .filter(segment => segment.length > 0)
      .join('.');
  }

  /**
   * Generates React component code.
   */
  private generateComponentCode(config: PageSkeletonConfig, componentName: string): string {
    const hasAccessibility = config.accessibility && Object.keys(config.accessibility).length > 0;
    const ariaLabel = hasAccessibility ? `aria-label={t('${config.descriptionI18nKey}')}` : '';

    return `import { useTranslation } from 'react-i18next';
import { useProductEntitlements } from '@ghatana/product-shell';
import type { ReactNode } from 'react';

/**
 * ${config.routeLabel} Page
 *
 * Route: ${config.routePath}
 * Group: ${config.routeGroup}
 * Minimum Role: ${config.minimumRole}
 *
 * Auto-generated from PHR route contract.
 * Do not edit manually - regenerate from contract.
 */
export default function ${componentName}(): ReactNode {
  const { t } = useTranslation();
  const { currentRole } = useProductEntitlements();

  return (
    <div className="page-container" role="main" ${ariaLabel}>
      <header className="page-header">
        <h1>{t('${config.i18nKey}')}</h1>
        <p className="page-description">
          {t('${config.descriptionI18nKey}')}
        </p>
      </header>

      <main className="page-content">
        {/* TODO: Implement ${config.routeLabel} page content */}
        <div className="placeholder-content">
          <p>{t('common.pageUnderConstruction')}</p>
        </div>
      </main>

      <footer className="page-footer">
        {/* TODO: Add page-specific footer content if needed */}
      </footer>
    </div>
  );
}
`;
  }

  /**
   * Extracts required imports from config.
   */
  private extractImports(config: PageSkeletonConfig): string[] {
    const imports = [
      'react-i18next',
      '@ghatana/product-shell',
    ];

    if (config.accessibility?.keyboardNav) {
      imports.push('@ghatana/design-system');
    }

    return imports;
  }

  /**
   * Extracts required dependencies from config.
   */
  private extractDependencies(config: PageSkeletonConfig): string[] {
    const deps = [
      'react-i18next',
      '@ghatana/product-shell',
    ];

    if (config.accessibility?.keyboardNav) {
      deps.push('@ghatana/design-system');
    }

    return deps;
  }
}

/**
 * Creates a PHR page skeleton generator instance.
 */
export function createPhrPageSkeletonGenerator(): PhrPageSkeletonGenerator {
  return new PhrPageSkeletonGenerator();
}

/**
 * Generates page skeletons from PHR route contract.
 */
export function generatePhrPageSkeletons(contract: PhrRouteContract): GeneratedPageSkeleton[] {
  return createPhrPageSkeletonGenerator().generatePageSkeletons(contract);
}
