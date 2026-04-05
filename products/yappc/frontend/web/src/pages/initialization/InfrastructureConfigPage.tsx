import React, { useState } from 'react';

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
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="mx-auto max-w-6xl">
        <h1 className="mb-2 text-3xl font-bold text-gray-900">Infrastructure Configuration</h1>
        <p className="mb-8 text-gray-600">Enable and configure platform infrastructure services.</p>

        <div className="flex gap-6">
          {/* Service List */}
          <div className="flex-1 space-y-6">
            {categories.map((cat) => (
              <div key={cat}>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-gray-500">
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
                            ? 'border-blue-400 bg-blue-50'
                            : 'bg-white hover:border-gray-300'
                        }`}
                      >
                        <span className="text-2xl">{svc.icon}</span>
                        <div className="min-w-0 flex-1">
                          <p className="font-semibold text-gray-900">{svc.name}</p>
                          <p className="text-sm text-gray-500">{svc.description}</p>
                        </div>
                        <button
                          onClick={(e) => { e.stopPropagation(); toggle(svc.id); }}
                          className={`relative h-6 w-11 rounded-full transition ${
                            svc.enabled ? 'bg-blue-600' : 'bg-gray-300'
                          }`}
                        >
                          <span
                            className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition ${
                              svc.enabled ? 'left-[22px]' : 'left-0.5'
                            }`}
                          />
                        </button>
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
                    <h3 className="font-semibold text-gray-900">{selected.name}</h3>
                    <span className={`text-xs font-medium ${
                      selected.enabled ? 'text-green-600' : 'text-gray-400'
                    }`}>
                      {selected.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </div>
                </div>
                <div className="space-y-3">
                  {Object.entries(selected.config).map(([k, v]) => (
                    <div key={k}>
                      <label className="mb-1 block text-xs font-medium text-gray-500">{k}</label>
                      <input
                        defaultValue={v}
                        className="w-full rounded border px-3 py-1.5 font-mono text-sm focus:border-blue-500 focus:outline-none"
                      />
                    </div>
                  ))}
                </div>
                <button className="mt-4 w-full rounded-md bg-blue-600 py-2 text-sm font-medium text-white hover:bg-blue-700">
                  Save Configuration
                </button>
              </div>
            ) : (
              <div className="rounded-lg border bg-white p-8 text-center text-sm text-gray-400 shadow-sm">
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
