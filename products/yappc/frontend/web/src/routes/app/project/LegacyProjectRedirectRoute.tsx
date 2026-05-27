import { useEffect } from 'react';
import { useTranslation } from '@ghatana/i18n';
import { useNavigate, useParams } from 'react-router';

import type { LegacyProjectRoutePolicy } from './legacyProjectRoutePolicy';

interface LegacyProjectRedirectRouteProps {
  readonly policy: LegacyProjectRoutePolicy;
}

export function LegacyProjectRedirectRoute({ policy }: LegacyProjectRedirectRouteProps) {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('common');

  useEffect(() => {
    if (projectId) {
      navigate(`/p/${projectId}/${policy.canonicalPhase}`, { replace: true });
    }
  }, [policy.canonicalPhase, projectId, navigate]);

  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <p className="text-sm text-fg-muted">{t(policy.redirectingKey)}</p>
      </div>
    </div>
  );
}
