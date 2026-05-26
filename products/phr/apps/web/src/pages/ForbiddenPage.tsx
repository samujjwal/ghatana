import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { t } from '../i18n/phrI18n';

export function ForbiddenPage(): React.ReactElement {
  const navigate = useNavigate();

  return (
    <div className="stack gap-lg" role="main">
      <Card>
        <CardHeader title={t('forbidden.title')} subheader="" />
        <CardContent>
          <p>{t('forbidden.message')}</p>
          <button
            type="button"
            className="btn btn--secondary"
            onClick={() => void navigate(-1)}
          >
            {t('forbidden.back')}
          </button>
        </CardContent>
      </Card>
    </div>
  );
}
