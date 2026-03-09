export interface JsonChange {
  kind: 'N' | 'D' | 'E' | 'A';
  path: string[];
  lhs?: any;
  rhs?: any;
  index?: number;
  item?: JsonChange;
}

export const diffJson = (
  obj1: Record<string, any>,
  obj2: Record<string, any>,
  options?: {
    sort?: boolean;
    showKeys?: boolean;
    full?: boolean;
  }
): JsonChange[] => [];
