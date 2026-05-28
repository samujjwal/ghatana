import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Badge, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchConditionDetail } from '../api/clinicalApi';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ConditionSummary } from '../types';

export function ConditionDetailPage(): React.ReactElement {
  const { conditionId } = useParams<{ conditionId: string }>();
  const { session } = usePhrSession();
  const [condition, setCondition] = useState<ConditionSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session || !conditionId) return;
    fetchConditionDetail(conditionId, session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then(setCondition)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('conditionDetail.error.load')))
      .finally(() => setLoading(false));
  }, [session, conditionId]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('conditionDetail.loading')}</div>;
  if (error) return <div className="error" role="alert">{t('conditionDetail.error.load')}: {error}</div>;
  if (!condition) return <div className="error" role="alert">{t('conditionDetail.error.load')}</div>;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={condition.name} subheader={t('conditionDetail.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('conditionDetail.status')}</dt><dd><Badge variant={condition.status === 'active' ? 'destructive' : 'secondary'}>{condition.status}</Badge></dd></div>
            <div><dt>{t('conditionDetail.code')}</dt><dd>{condition.code || condition.icdCode || '-'}</dd></div>
            {condition.onsetDate && (
              <div><dt>{t('conditionDetail.onsetDate')}</dt><dd>{new Date(condition.onsetDate).toLocaleDateString()}</dd></div>
            )}
            {condition.resolvedDate && (
              <div><dt>{t('conditionDetail.resolvedDate')}</dt><dd>{new Date(condition.resolvedDate).toLocaleDateString()}</dd></div>
            )}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
