/**
 * YAPPC-T08: Product Mobile Screen Skeleton Generator
 * 
 * Generates mobile screen skeletons with secure session/i18n/a11y defaults.
 * Produces React Native screen code with proper structure, secure session handling, i18n, and accessibility.
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
  description?: string;
  group?: string;
  minimumRole?: string;
  surface?: string[];
  i18nKey?: string;
  descriptionI18nKey?: string;
  accessibility?: Record<string, boolean>;
  metadata?: Record<string, unknown>;
}

export interface MobileScreenConfig {
  productId: string;
  routePath: string;
  routeLabel: string;
  routeDescription: string;
  minimumRole: string;
  i18nKey: string;
  descriptionI18nKey: string;
  accessibility: Record<string, boolean>;
}

export interface GeneratedMobileScreen {
  filePath: string;
  screenName: string;
  code: string;
  imports: string[];
  dependencies: string[];
}

/**
 * Generates React Native mobile screen skeletons with secure session, i18n, and a11y defaults.
 */
export class MobileScreenGenerator {
  /**
   * Generates mobile screen skeletons for all mobile routes.
   */
  generateMobileScreens(contract: ProductRouteContract): GeneratedMobileScreen[] {
    return contract.routes
      .filter(route => this.shouldGenerateMobileScreen(route))
      .map(route => this.generateMobileScreen(contract.product, route));
  }

  /**
   * Determines if a mobile screen should be generated for the route.
   */
  private shouldGenerateMobileScreen(route: ProductRoute): boolean {
    const surface = route.surface || [];
    return surface.includes('mobile') || route.path.startsWith('/mobile/');
  }

  /**
   * Generates a single mobile screen skeleton.
   */
  private generateMobileScreen(productId: string, route: ProductRoute): GeneratedMobileScreen {
    const config: MobileScreenConfig = {
      productId,
      routePath: route.path,
      routeLabel: route.label,
      routeDescription: route.description || '',
      minimumRole: route.minimumRole || 'viewer',
      i18nKey: route.i18nKey || `${productId}.routes.${this.pathToKey(route.path)}.label`,
      descriptionI18nKey: route.descriptionI18nKey || `${productId}.routes.${this.pathToKey(route.path)}.description`,
      accessibility: route.accessibility || this.defaultAccessibility(),
    };

    const screenName = this.pathToScreenName(route.path);
    const filePath = this.pathToFilePath(route.path);
    const code = this.generateScreenCode(config, screenName);
    const imports = this.extractImports(config);
    const dependencies = this.extractDependencies(config);

    return {
      filePath,
      screenName,
      code,
      imports,
      dependencies,
    };
  }

  /**
   * Converts route path to screen name.
   */
  private pathToScreenName(path: string): string {
    return path
      .split(/[/_-]+/)
      .filter(segment => segment.length > 0)
      .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join('');
  }

  /**
   * Converts route path to file path.
   */
  private pathToFilePath(path: string): string {
    const segments = path.split(/[/_-]+/).filter(segment => segment.length > 0);
    if (segments.length === 0) {
      return 'src/screens/HomeScreen.tsx';
    }
    const screenName = this.pathToScreenName(path);
    return `src/screens/${screenName}.tsx`;
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
   * Returns default accessibility configuration for mobile screens.
   */
  private defaultAccessibility(): Record<string, boolean> {
    return {
      ariaLabel: true,
      keyboardNav: true,
      screenReader: true,
      highContrast: true,
      reducedMotion: true,
    };
  }

  /**
   * Generates React Native screen code with secure session, i18n, and a11y defaults.
   */
  private generateScreenCode(config: MobileScreenConfig, screenName: string): string {
    const accessibilityProps = this.generateAccessibilityProps(config.accessibility);

    return `import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useSecureSession } from '@ghatana/mobile-security';
import { useProductEntitlements } from '@ghatana/product-shell-mobile';

/**
 * ${config.routeLabel} Screen
 *
 * Route: ${config.routePath}
 * Minimum Role: ${config.minimumRole}
 *
 * Auto-generated from Kernel product route contract.
 *
 * Features:
 * - Secure session handling
 * - i18n support
 * - Accessibility defaults
 */
export default function ${screenName}(): React.JSX.Element {
  const { t } = useTranslation();
  const { hasRole } = useProductEntitlements();
  const { isSessionValid } = useSecureSession();

  // Secure session validation
  if (!isSessionValid()) {
    return (
      <View style={styles.container}>
        <Text style={styles.errorText}>{t('common.sessionExpired')}</Text>
      </View>
    );
  }

  // Role-based access control
  if (!hasRole('${config.minimumRole}')) {
    return (
      <View style={styles.container}>
        <Text style={styles.errorText}>{t('common.accessDenied')}</Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.contentContainer}
      ${accessibilityProps}
    >
      <View style={styles.header}>
        <Text style={styles.title} accessibilityLabel={t('${config.descriptionI18nKey}')}>
          {t('${config.i18nKey}')}
        </Text>
        <Text style={styles.description}>
          {t('${config.descriptionI18nKey}')}
        </Text>
      </View>

      <View style={styles.content}>
        <Text nativeID="${config.productId}-${config.routePath.replaceAll('/', '-')}" style={styles.description}>
          {t('${config.descriptionI18nKey}')}
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  contentContainer: {
    padding: 16,
  },
  header: {
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#000000',
    marginBottom: 8,
  },
  description: {
    fontSize: 16,
    color: '#666666',
  },
  content: {
    flex: 1,
  },
  errorText: {
    fontSize: 16,
    color: '#FF0000',
    textAlign: 'center',
    marginTop: 32,
  },
});
`;
  }

  /**
   * Generates accessibility props based on configuration.
   */
  private generateAccessibilityProps(accessibility: Record<string, boolean>): string {
    const props: string[] = [];
    
    if (accessibility.ariaLabel) {
      props.push('accessible={true}');
    }
    if (accessibility.screenReader) {
      props.push('accessibilityRole="main"');
    }
    
    return props.length > 0 ? props.join('\n      ') : '';
  }

  /**
   * Extracts required imports from config.
   */
  private extractImports(config: MobileScreenConfig): string[] {
    return [
      'react',
      'react-native',
      'react-i18next',
      '@ghatana/mobile-security',
      '@ghatana/product-shell-mobile',
    ];
  }

  /**
   * Extracts required dependencies from config.
   */
  private extractDependencies(config: MobileScreenConfig): string[] {
    return [
      'react',
      'react-native',
      'react-i18next',
      '@ghatana/mobile-security',
      '@ghatana/product-shell-mobile',
    ];
  }
}

/**
 * Creates a Product mobile screen generator instance.
 */
export function createMobileScreenGenerator(): MobileScreenGenerator {
  return new MobileScreenGenerator();
}

/**
 * Generates mobile screen skeletons from Product route contract.
 */
export function generateMobileScreens(contract: ProductRouteContract): GeneratedMobileScreen[] {
  return createMobileScreenGenerator().generateMobileScreens(contract);
}
