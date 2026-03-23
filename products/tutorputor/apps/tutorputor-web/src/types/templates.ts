export type TemplateCategory = 'module' | 'assessment' | 'simulation' | 'pathway';
export type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface Template {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  status: TemplateStatus;
  tags: string[];
  thumbnail?: string;
  authorId: string;
  authorName?: string;
  isPublic: boolean;
  usageCount: number;
  rating?: number;
  createdAt: string;
  updatedAt: string;
  content: unknown;
}

export interface TemplateInput {
  name: string;
  description: string;
  category: TemplateCategory;
  tags?: string[];
  thumbnail?: string;
  isPublic?: boolean;
  content: unknown;
}

export interface TemplateFilters {
  category?: TemplateCategory;
  status?: TemplateStatus;
  tags?: string[];
  searchQuery?: string;
  authorId?: string;
}
