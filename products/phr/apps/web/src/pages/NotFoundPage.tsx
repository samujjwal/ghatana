import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { t } from '../i18n/phrI18n';

export function NotFoundPage(): React.ReactElement {
  const navigate = useNavigate();

  return (
    <div className="stack gap-lg" role="main">
      <Card>
        <CardHeader title={t('notFound.title')} subheader="" />
        <CardContent>
          <p>{t('notFound.message')}</p>
          <Button
            type="button"
            onClick={() => void navigate('/dashboard')}
          >
            {t('notFound.back')}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
