import { Badge } from 'yappc-ui/components/ui/badge';
import { InfoCircledIcon, BrainIcon, GearIcon } from '@radix-ui/react-icons';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from 'yappc-ui/components/ui/tooltip';

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
  const Icon = isRuleBased ? GearIcon : BrainIcon;
  const label = isRuleBased ? 'Rule-based assist' : 'Model-backed assist';
  const variant = isRuleBased ? 'secondary' : 'default';
  
  const content = (
    <Badge variant={variant} className="gap-1.5">
      <Icon className={size === 'sm' ? 'h-3 w-3' : 'h-4 w-4'} />
      <span className={size === 'sm' ? 'text-xs' : 'text-sm'}>{label}</span>
      {confidence !== undefined && (
        <span className="text-muted-foreground text-xs">
          {Math.round(confidence * 100)}%
        </span>
      )}
      {(rationale || sources) && (
        <InfoCircledIcon className="h-3 w-3 text-muted-foreground cursor-help" />
      )}
    </Badge>
  );

  if (!rationale && !sources) {
    return content;
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          {content}
        </TooltipTrigger>
        <TooltipContent side="top" className="max-w-xs">
          <div className="space-y-2">
            {rationale && (
              <div>
                <p className="font-medium text-xs">Rationale:</p>
                <p className="text-xs text-muted-foreground">{rationale}</p>
              </div>
            )}
            {sources && sources.length > 0 && (
              <div>
                <p className="font-medium text-xs">Sources:</p>
                <ul className="text-xs text-muted-foreground list-disc list-inside">
                  {sources.map((source) => (
                    <li key={source.id}>{source.name}</li>
                  ))}
                </ul>
              </div>
            )}
            {confidence !== undefined && (
              <div>
                <p className="font-medium text-xs">Confidence: {Math.round(confidence * 100)}%</p>
              </div>
            )}
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
