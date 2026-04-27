export type RequirementPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type RequirementStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'IMPLEMENTED';

export interface RequirementVersion {
  id: string;
  version: number;
  summary: string;
  createdBy: string;
  createdAt: string;
}

export interface RequirementRecord {
  id: string;
  title: string;
  description: string;
  priority: RequirementPriority;
  status: RequirementStatus;
  tags?: string[];
  createdAt: string;
  updatedAt: string;
  versions: RequirementVersion[];
}