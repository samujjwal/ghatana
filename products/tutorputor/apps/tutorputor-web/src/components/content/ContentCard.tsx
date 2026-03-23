import { Card } from '@ghatana/design-system';
import type { Content } from '@/types/content';

export interface ContentCardProps {
  content: Content;
  onClick?: () => void;
}

export function ContentCard({ content, onClick }: ContentCardProps) {
  return (
    <Card className="cursor-pointer hover:shadow-lg transition-shadow" onClick={onClick}>
      <div className="p-4">
        <h3 className="text-lg font-semibold">{content.title}</h3>
        <p className="text-sm text-gray-600 mt-1">{content.type}</p>
        <div className="flex gap-2 mt-2">
          {content.tags?.map((tag) => (
            <span key={tag} className="text-xs bg-gray-100 px-2 py-1 rounded">
              {tag}
            </span>
          ))}
        </div>
      </div>
    </Card>
  );
}
