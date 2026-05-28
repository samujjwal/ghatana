import React from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { t } from '../i18n/phrI18n';

interface FeatureFlagPageProps {
  readonly routePath: string;
}

/**
 * Deferred page rendered for preview routes that are present in the IA
 * contract but blocked from production navigation.
 */
export function FeatureFlagPage({ routePath }: FeatureFlagPageProps): React.ReactElement {
  return (
    <Card>
      <CardHeader
        title={t('featureFlag.comingSoon')}
        subheader={t('featureFlag.deferred')}
      />
      <CardContent>
        <p className="muted" data-testid="feature-flag-path" data-route={routePath}>
          {t('featureFlag.comingSoon')}
        </p>
      </CardContent>
    </Card>
  );
}
