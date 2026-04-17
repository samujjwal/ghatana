import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

interface DomainRecord {
  id: string;
  domain: string;
  title: string;
  description: string;
  author?: string;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt?: string;
}

interface DomainPayload {
  domain: string;
  title: string;
  description: string;
}

interface DomainResponse {
  data: DomainRecord[];
}

interface MutationResponse {
  data: DomainRecord;
}

interface ValidationErrors {
  domain?: string;
  title?: string;
  description?: string;
}

const DOMAIN_OPTIONS = [
  'PHYSICS',
  'CHEMISTRY',
  'BIOLOGY',
  'MATHEMATICS',
  'COMPUTER_SCIENCE',
] as const;

const EMPTY_FORM: DomainPayload = {
  domain: 'PHYSICS',
  title: '',
  description: '',
};

async function parseJson<T>(response: Response): Promise<T> {
  return response.json() as Promise<T>;
}

async function fetchDomains(): Promise<DomainRecord[]> {
  const response = await fetch('/admin/api/v1/content/domains');
  if (!response.ok) {
    throw new Error(`Error loading domains: ${response.statusText || response.status}`);
  }

  const payload = await parseJson<DomainResponse>(response);
  return payload.data;
}

function validatePayload(payload: DomainPayload): ValidationErrors {
  const errors: ValidationErrors = {};

  if (!payload.domain.trim()) {
    errors.domain = 'Domain type is required';
  }

  if (!payload.title.trim()) {
    errors.title = 'Title is required';
  }

  if (!payload.description.trim()) {
    errors.description = 'Description is required';
  }

  return errors;
}

function statusBadgeClass(status: DomainRecord['status']): string {
  return status === 'PUBLISHED'
    ? 'bg-green-100 text-green-800'
    : 'bg-gray-100 text-gray-800';
}

export function DomainEditorPage(): JSX.Element {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingDomainId, setEditingDomainId] = useState<string | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [formData, setFormData] = useState<DomainPayload>(EMPTY_FORM);
  const [validationErrors, setValidationErrors] = useState<ValidationErrors>({});

  const { data: domains = [], isLoading, error } = useQuery({
    queryKey: ['domains'],
    queryFn: fetchDomains,
  });

  const upsertMutation = useMutation({
    mutationFn: async (payload: DomainPayload): Promise<DomainRecord> => {
      const endpoint = editingDomainId
        ? `/admin/api/v1/content/domains/${editingDomainId}`
        : '/admin/api/v1/content/domains';
      const method = editingDomainId ? 'PATCH' : 'POST';
      const response = await fetch(endpoint, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`Failed to ${editingDomainId ? 'update' : 'create'} domain`);
      }

      const result = await parseJson<MutationResponse>(response);
      return result.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['domains'] });
      setShowForm(false);
      setEditingDomainId(null);
      setFormData(EMPTY_FORM);
      setValidationErrors({});
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (domainId: string): Promise<void> => {
      const response = await fetch(`/admin/api/v1/content/domains/${domainId}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        throw new Error('Failed to delete domain');
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['domains'] });
      setPendingDeleteId(null);
    },
  });

  const publishMutation = useMutation({
    mutationFn: async (domainId: string): Promise<void> => {
      const response = await fetch(`/admin/api/v1/content/domains/${domainId}/publish`, {
        method: 'POST',
      });

      if (!response.ok) {
        throw new Error('Failed to publish domain');
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['domains'] });
    },
  });

  const handleCreate = (): void => {
    setEditingDomainId(null);
    setFormData(EMPTY_FORM);
    setValidationErrors({});
    setShowForm(true);
  };

  const handleEdit = (domain: DomainRecord): void => {
    setEditingDomainId(domain.id);
    setFormData({
      domain: domain.domain,
      title: domain.title,
      description: domain.description,
    });
    setValidationErrors({});
    setShowForm(true);
  };

  const handleCancel = (): void => {
    setEditingDomainId(null);
    setValidationErrors({});
    setFormData(EMPTY_FORM);
    setShowForm(false);
  };

  const handleSubmit = (): void => {
    const errors = validatePayload(formData);
    setValidationErrors(errors);

    if (Object.keys(errors).length > 0) {
      return;
    }

    upsertMutation.mutate(formData);
  };

  if (isLoading) {
    return <div>Loading domains...</div>;
  }

  if (error instanceof Error) {
    return <div>Error loading domains</div>;
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Domain Editor</h1>
          <p className="text-sm text-gray-600">Manage learning domains and publish them for authoring flows.</p>
        </div>
        <button className="rounded bg-black px-4 py-2 text-white" onClick={handleCreate}>
          New Domain
        </button>
      </div>

      {showForm ? (
        <div className="rounded border border-gray-200 p-4">
          <h2 className="mb-4 text-lg font-semibold">{editingDomainId ? 'Edit Domain' : 'Create Domain'}</h2>

          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-sm font-medium" htmlFor="domain-type">
                Domain Type
              </label>
              <select
                id="domain-type"
                value={formData.domain}
                onChange={(event) => setFormData({ ...formData, domain: event.target.value })}
              >
                {DOMAIN_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
              {validationErrors.domain ? <p>{validationErrors.domain}</p> : null}
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium" htmlFor="domain-title">
                Title
              </label>
              <input
                id="domain-title"
                value={formData.title}
                onChange={(event) => setFormData({ ...formData, title: event.target.value })}
              />
              {validationErrors.title ? <p>{validationErrors.title}</p> : null}
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium" htmlFor="domain-description">
                Description
              </label>
              <textarea
                id="domain-description"
                value={formData.description}
                onChange={(event) => setFormData({ ...formData, description: event.target.value })}
              />
              {validationErrors.description ? <p>{validationErrors.description}</p> : null}
            </div>

            <div className="flex gap-2">
              <button className="rounded bg-black px-4 py-2 text-white" onClick={handleSubmit}>
                {editingDomainId ? 'Save' : 'Create'}
              </button>
              <button className="rounded border border-gray-300 px-4 py-2" onClick={handleCancel}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {domains.length === 0 ? <div>No domains yet</div> : null}

      <div className="space-y-3">
        {domains.map((domain) => (
          <div key={domain.id} className="rounded border border-gray-200 p-4">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-semibold">{domain.title}</h3>
                  <span className={`rounded px-2 py-1 text-xs ${statusBadgeClass(domain.status)}`}>
                    {domain.status}
                  </span>
                </div>
                <p>{domain.description}</p>
              </div>

              <div className="flex gap-2">
                <button onClick={() => handleEdit(domain)}>Edit</button>
                <button onClick={() => setPendingDeleteId(domain.id)}>Delete</button>
                {domain.status === 'DRAFT' ? (
                  <button onClick={() => publishMutation.mutate(domain.id)}>Publish</button>
                ) : null}
              </div>
            </div>

            {pendingDeleteId === domain.id ? (
              <div className="mt-4 rounded border border-red-200 bg-red-50 p-3">
                <p>Are you sure you want to delete this domain?</p>
                <div className="mt-2 flex gap-2">
                  <button onClick={() => deleteMutation.mutate(domain.id)}>Confirm</button>
                  <button onClick={() => setPendingDeleteId(null)}>Cancel</button>
                </div>
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}