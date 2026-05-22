export interface BadgeProps {
  readonly label: string;
  readonly tone: 'neutral' | 'critical';
}

export function renderBadgePreview(props: BadgeProps): string {
  return `<span data-tone="${props.tone}">${props.label}</span>`;
}
