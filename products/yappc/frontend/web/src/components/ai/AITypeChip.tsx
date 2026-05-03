import { Badge, Box, Tooltip, Typography } from '@ghatana/design-system';
import {
  Brain,
  CircleHelp,
  Cog,
} from 'lucide-react';

/**
 * AI type chip component to distinguish rule-based vs model-backed AI features
 * 
 * @doc.type component
 * @doc.purpose Display AI source type (rule-based vs model-backed) with confidence and sources
 * @doc.layer frontend
 */
export interface AITypeChipProps {
  type: 'rule-based' | 'model-backed';
  confidence?: number;
  rationale?: string;
  sources?: Array<{ id: string; name: string }>;
  size?: 'sm' | 'md' | 'lg';
}

export function AITypeChip({ 
  type, 
  confidence, 
  rationale, 
  sources,
  size = 'md' 
}: AITypeChipProps) {
  const isRuleBased = type === 'rule-based';
  const Icon = isRuleBased ? Cog : Brain;
  const label = isRuleBased ? 'Rule-based assist' : 'Model-backed assist';
  const badgeColor = isRuleBased ? 'default' : 'primary';
  
  const content = (
    <Badge color={badgeColor} className="inline-flex items-center gap-1.5">
      <Icon className={size === 'sm' ? 'h-3 w-3' : 'h-4 w-4'} />
      <span className={size === 'sm' ? 'text-xs' : 'text-sm'}>{label}</span>
      {confidence !== undefined && (
        <span className="text-muted-foreground text-xs">
          {Math.round(confidence * 100)}%
        </span>
      )}
      {(rationale || sources) && (
        <CircleHelp className="h-3 w-3 cursor-help text-muted-foreground" />
      )}
    </Badge>
  );

  if (!rationale && !sources) {
    return content;
  }

  return (
    <Tooltip
      title={
        <Box className="space-y-2">
          {rationale ? (
            <Box>
              <Typography variant="caption" className="font-medium">
                Rationale:
              </Typography>
              <Typography variant="caption" className="block text-muted-foreground">
                {rationale}
              </Typography>
            </Box>
          ) : null}
          {sources && sources.length > 0 ? (
            <Box>
              <Typography variant="caption" className="font-medium">
                Sources:
              </Typography>
              <ul className="list-inside list-disc text-xs text-muted-foreground">
                {sources.map((source) => (
                  <li key={source.id}>{source.name}</li>
                ))}
              </ul>
            </Box>
          ) : null}
          {confidence !== undefined ? (
            <Typography variant="caption" className="font-medium">
              Confidence: {Math.round(confidence * 100)}%
            </Typography>
          ) : null}
        </Box>
      }
      placement="top"
    >
      <span>{content}</span>
    </Tooltip>
  );
}
