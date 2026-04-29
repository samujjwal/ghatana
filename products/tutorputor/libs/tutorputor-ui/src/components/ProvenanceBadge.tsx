export interface ContentProvenance {
  authorId?: string;
  publishedAt?: string;
  source?: string;
  version?: string;
}

export interface ProvenanceBadgeProps {
  provenance: ContentProvenance;
  className?: string;
}

export function ProvenanceBadge(_props: ProvenanceBadgeProps) {
  return null;
}
