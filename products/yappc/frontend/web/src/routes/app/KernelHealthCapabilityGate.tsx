import { useTranslation } from '@ghatana/i18n';

import { useCapabilityGate } from '../../hooks/useCapabilityGate';

interface KernelHealthCapabilityGateProps {
  readonly children: React.ReactNode;
}

export function KernelHealthCapabilityGate({ children }: KernelHealthCapabilityGateProps) {
  const { granted, reason } = useCapabilityGate('kernel-health:read');
  const { t } = useTranslation('common');

  if (!granted) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center" data-testid="kernel-health-unavailable">
        <div className="space-y-2 text-center">
          <p className="text-sm text-fg-muted">
            {reason === 'insufficient-role'
              ? t('kernelHealth.denied.permission')
              : reason === 'unauthenticated'
                ? t('kernelHealth.denied.login')
                : t('kernelHealth.denied.unavailable')}
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
