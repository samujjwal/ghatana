import { Input, Select } from '@ghatana/design-system';

export interface ContentFiltersProps {
  filters: {
    domain?: string;
    difficulty?: string;
    searchQuery?: string;
  };
  onChange: (filters: ContentFiltersProps['filters']) => void;
}

export function ContentFilters({ filters, onChange }: ContentFiltersProps) {
  return (
    <div className="flex gap-4 p-4 bg-white rounded-lg shadow">
      <Input
        placeholder="Search content..."
        value={filters.searchQuery || ''}
        onChange={(e) => onChange({ ...filters, searchQuery: e.target.value })}
        className="flex-1"
      />
      <Select
        value={filters.domain || ''}
        onChange={(e) => onChange({ ...filters, domain: e.target.value })}
      >
        <option value="">All Domains</option>
        <option value="MATH">Math</option>
        <option value="SCIENCE">Science</option>
        <option value="TECH">Technology</option>
      </Select>
      <Select
        value={filters.difficulty || ''}
        onChange={(e) => onChange({ ...filters, difficulty: e.target.value })}
      >
        <option value="">All Levels</option>
        <option value="INTRO">Intro</option>
        <option value="INTERMEDIATE">Intermediate</option>
        <option value="ADVANCED">Advanced</option>
      </Select>
    </div>
  );
}
