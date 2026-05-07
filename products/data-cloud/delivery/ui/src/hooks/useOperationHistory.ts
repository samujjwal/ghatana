/**
 * useOperationHistory
 *
 * Tracks client-side records of mutations and governance actions performed by
 * the current user in this session. Designed to be extended with an optional
 * async backend fetch once a server-side audit API is available.
 *
 * @doc.type hook
 * @doc.purpose Session-scoped operation audit history
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */

import { useCallback, useState } from 'react';

export type OperationOutcome = 'success' | 'failure' | 'pending';

export interface OperationRecord {
  id: string;
  timestamp: string;
  action: string;
  resource: string;
  outcome: OperationOutcome;
  detail?: string;
  user?: string;
}

export interface UseOperationHistoryResult {
  records: OperationRecord[];
  addRecord: (record: Omit<OperationRecord, 'id' | 'timestamp'>) => OperationRecord;
  updateOutcome: (id: string, outcome: OperationOutcome, detail?: string) => void;
  clearRecords: () => void;
}

let idCounter = 0;

/**
 * Returns a session-scoped operation history.
 *
 * Usage:
 * ```ts
 * const { addRecord, records } = useOperationHistory();
 * const pending = addRecord({ action: 'Classify retention', resource: 'users', outcome: 'pending' });
 * mutation.mutate(payload, {
 *   onSuccess: () => updateOutcome(pending.id, 'success'),
 *   onError: (err) => updateOutcome(pending.id, 'failure', err.message),
 * });
 * ```
 */
export function useOperationHistory(): UseOperationHistoryResult {
  const [records, setRecords] = useState<OperationRecord[]>([]);

  const addRecord = useCallback(
    (fields: Omit<OperationRecord, 'id' | 'timestamp'>): OperationRecord => {
      idCounter += 1;
      const record: OperationRecord = {
        id: `op-${idCounter}`,
        timestamp: new Date().toISOString(),
        ...fields,
      };
      setRecords((prev) => [record, ...prev]);
      return record;
    },
    [],
  );

  const updateOutcome = useCallback(
    (id: string, outcome: OperationOutcome, detail?: string) => {
      setRecords((prev) =>
        prev.map((r) =>
          r.id === id
            ? { ...r, outcome, ...(detail !== undefined ? { detail } : {}) }
            : r,
        ),
      );
    },
    [],
  );

  const clearRecords = useCallback(() => setRecords([]), []);

  return { records, addRecord, updateOutcome, clearRecords };
}
