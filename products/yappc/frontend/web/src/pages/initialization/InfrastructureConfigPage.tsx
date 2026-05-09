import React, { useState } from 'react';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { useI18n } from '../../i18n/I18nProvider';

// ============================================================================
// Types
// ============================================================================

interface InfraService {
  id: string;
  name: string;
  icon: string;
  description: string;
  category: 'compute' | 'storage' | 'network' | 'observability';
  enabled: boolean;
  config: Record<string, string>;
}

// ============================================================================
// Mock data
// ============================================================================

const SERVICES: InfraService[] = [
  { id: 'k8s', name: 'Kubernetes', icon: '☸️', description: 'Container orchestration', category: 'compute', enabled: true, config: { cluster: 'yappc-dev', namespace: 'default', replicas: '3' } },
  { id: 'docker', name: 'Docker Registry', icon: '🐳', description: 'Container image registry', category: 'compute', enabled: true, config: { registry: 'ghcr.io/ghatana', pushPolicy: 'on-merge' } },
  { id: 'postgres', name: 'PostgreSQL', icon: '🐘', description: 'Relational database', category: 'storage', enabled: true, config: { version: '16', poolSize: '20', backupSchedule: '0 2 * * *' } },
  { id: 'redis', name: 'Redis / Dragonfly', icon: '🔴', description: 'In-memory cache & state', category: 'storage', enabled: true, config: { maxMemory: '512mb', eviction: 'allkeys-lru' } },
  { id: 'kafka', name: 'Kafka', icon: '📡', description: 'Event streaming platform', category: 'network', enabled: false, config: { brokers: '3', partitions: '12', retention: '7d' } },
  { id: 'vault', name: 'HashiCorp Vault', icon: '🔐', description: 'Secrets management', category: 'network', enabled: true, config: { address: 'https://vault.internal', engine: 'kv-v2' } },
  { id: 'prom', name: 'Prometheus', icon: '📊', description: 'Metrics collection', category: 'observability', enabled: true, config: { scrapeInterval: '15s', retention: '30d' } },
  { id: 'otel', name: 'OpenTelemetry', icon: '🔭', description: 'Distributed tracing', category: 'observability', enabled: true, config: { samplingRate: '0.1', exporter: 'otlp' } },
];

const CATEGORY_LABELS: Record<string, string> = {
  compute: '💻 Compute',
  storage: '💾 Storage',
  network: '🌐 Network',
  observability: '📈 Observability',
};

// ============================================================================
// Component
// ============================================================================

const InfrastructureConfigPage: React.FC = () => {
  const { t } = useI18n();
  const [services, setServices] = useState(SERVICES);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const toggle = (id: string): void => {
    setServices((prev) =>
      prev.map((s) => (s.id === id ? { ...s, enabled: !s.enabled } : s)),
    );
  };

  const selected = services.find((s) => s.id === selectedId);
  const categories = [...new Set(services.map((s) => s.category))];

  return (
    <div className="min-h-screen bg-surface-muted p-6">
      <div className="mx-auto max-w-6xl">
        <h1 className="mb-2 text-3xl font-bold text-fg">Infrastructure Configuration</h1>
        <p className="mb-8 text-fg-muted">Enable and configure platform infrastructure services.</p>

        <div className="flex gap-6">
          {/* Service List */}
          <div className="flex-1 space-y-6">
            {categories.map((cat) => (
              <div key={cat}>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-fg-muted">
                  {CATEGORY_LABELS[cat]}
                </h2>
                <div className="space-y-2">
                  {services
                    .filter((s) => s.category === cat)
                    .map((svc) => (
                      <div
                        key={svc.id}
                        onClick={() => setSelectedId(svc.id)}
                        className={`flex cursor-pointer items-center gap-4 rounded-lg border p-4 transition ${
                          selectedId === svc.id
                            ? 'border-info-border bg-info-bg'
                            : 'bg-white hover:border-border'
                        }`}
                      >
                        <span className="text-2xl">{svc.icon}</span>
                        <div className="min-w-0 flex-1">
                          <p className="font-semibold text-fg">{svc.name}</p>
                          <p className="text-sm text-fg-muted">{svc.description}</p>
                        </div>
                        <Button
                          onClick={(e) => { e.stopPropagation(); toggle(svc.id); }}
                          variant="ghost"
                          size="sm"
                          className={`relative h-6 w-11 rounded-full transition ${
                            svc.enabled ? 'bg-primary' : 'bg-surface-muted'
                          }`}
                          aria-pressed={svc.enabled}
                          aria-label={
                            svc.enabled
                              ? t('infrastructureConfig.disableService', { name: svc.name })
                              : t('infrastructureConfig.enableService', { name: svc.name })
                          }
                        >
                          <span
                            className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition ${
                              svc.enabled ? 'left-[22px]' : 'left-0.5'
                            }`}
                          />
                        </Button>
                      </div>
                    ))}
                </div>
              </div>
            ))}
          </div>

          {/* Config Panel */}
          <div className="w-80 shrink-0">
            {selected ? (
              <div className="sticky top-6 rounded-lg border bg-white p-5 shadow-sm">
                <div className="mb-4 flex items-center gap-3">
                  <span className="text-3xl">{selected.icon}</span>
                  <div>
                    <h3 className="font-semibold text-fg">{selected.name}</h3>
                    <span className={`text-xs font-medium ${
                      selected.enabled ? 'text-success-color' : 'text-fg-muted'
                    }`}>
                      {selected.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </div>
                </div>
                <div className="space-y-3">
                  {Object.entries(selected.config).map(([k, v]) => (
                    <div key={k}>
                      <label className="mb-1 block text-xs font-medium text-fg-muted">{k}</label>
                      <Input
                        defaultValue={v}
                        className="w-full rounded border px-3 py-1.5 font-mono text-sm focus:border-info-border focus:outline-none"
                      />
                    </div>
                  ))}
                </div>
                <Button className="mt-4 w-full rounded-md bg-primary py-2 text-sm font-medium text-white hover:bg-info-bg">
                  Save Configuration
                </Button>
              </div>
            ) : (
              <div className="rounded-lg border bg-white p-8 text-center text-sm text-fg-muted shadow-sm">
                Select a service to configure
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default InfrastructureConfigPage;
