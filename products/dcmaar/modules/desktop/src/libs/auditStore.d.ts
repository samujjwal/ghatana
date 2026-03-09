export interface AuditEntry {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  userId: string;
  timestamp: string;
  details: Record<string, any>;
  ipAddress?: string;
  userAgent?: string;
}

export const loadAuditEntries = async (options?: {
  limit?: number;
  offset?: number;
  entityType?: string;
  entityId?: string;
  action?: string;
  userId?: string;
  startDate?: Date;
  endDate?: Date;
}): Promise<{ entries: AuditEntry[]; total: number }> => {
  return { entries: [], total: 0 };
};

export const saveAuditEntry = async (entry: Omit<AuditEntry, 'id' | 'timestamp'>): Promise<AuditEntry> => {
  return {} as AuditEntry;
};
