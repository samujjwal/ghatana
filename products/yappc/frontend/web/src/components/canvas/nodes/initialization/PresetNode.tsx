// ============================================================================
// PresetNode - Canvas node for preset/template visualization
//
// Features:
// - Preset category and tags
// - Stack configuration display
// - Feature list
// - Requirements list
// - Cost and time estimates
// - Popularity indicator
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import {
  Layers,
  Star,
  Clock,
  DollarSign,
  Check,
  AlertCircle,
  Zap,
  Code,
  Server,
  Database,
  Globe,
  Package,
  Rocket,
  Layout,
  Box,
  Grid3X3,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import { Button } from '../../../ui/Button';

type PresetCategory =
  | 'STARTER'
  | 'FULL_STACK'
  | 'API_ONLY'
  | 'STATIC_SITE'
  | 'MONOREPO'
  | 'MICROSERVICES'
  | 'CUSTOM';

interface PresetStack {
  language?: string;
  framework?: string;
  frontend?: string;
  backend?: string;
  database?: string;
}

interface PresetConfig {
  stack: PresetStack;
  includeRepository?: boolean;
  includeHosting?: boolean;
  includeDatabase?: boolean;
  includeCache?: boolean;
  includeStorage?: boolean;
  includeCICD?: boolean;
  includeMonitoring?: boolean;
}

interface Preset {
  id: string;
  name: string;
  description: string;
  icon?: string;
  category: PresetCategory;
  popularity: number;
  estimatedSetupTime: number;
  estimatedMonthlyCost: number;
  config: PresetConfig;
  features: string[];
  requirements?: string[];
  tags: string[];
}

export interface PresetNodeData extends Record<string, unknown> {
  preset: Preset;
  isSelected?: boolean;
  onSelect?: () => void;
  onPreview?: () => void;
}

type PresetCanvasNode = Node<PresetNodeData, 'preset'>;

const categoryConfig: Record<
  PresetCategory,
  { color: string; bgColor: string; icon: typeof Layers; label: string }
> = {
  STARTER: { color: 'text-success-color', bgColor: 'bg-success-bg', icon: Rocket, label: 'Starter' },
  FULL_STACK: { color: 'text-info-color', bgColor: 'bg-info-bg', icon: Layers, label: 'Full Stack' },
  API_ONLY: { color: 'text-info-color', bgColor: 'bg-info-bg', icon: Server, label: 'API Only' },
  STATIC_SITE: { color: 'text-info-color', bgColor: 'bg-info-bg', icon: Layout, label: 'Static Site' },
  MONOREPO: { color: 'text-warning-color', bgColor: 'bg-warning-bg', icon: Box, label: 'Monorepo' },
  MICROSERVICES: { color: 'text-warning-color', bgColor: 'bg-warning-bg', icon: Grid3X3, label: 'Microservices' },
  CUSTOM: { color: 'text-fg-muted', bgColor: 'bg-muted', icon: Package, label: 'Custom' },
};

function PresetNode({ data }: NodeProps<PresetCanvasNode>) {
  const { preset, isSelected, onSelect, onPreview } = data;
  const categoryInfo = categoryConfig[preset.category];
  const CategoryIcon = categoryInfo.icon;

  const popularityStars = Math.min(5, Math.ceil(preset.popularity / 200));

  return (
    <div
      className={cn(
        'bg-surface rounded-lg border shadow-xl min-w-[340px] max-w-[400px] transition-all',
        isSelected ? 'border-info-border ring-2 ring-info-border/30' : 'border-border'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-info-color" />
      <Handle type="source" position={Position.Right} className="!bg-info-color" />

      {/* Header */}
      <div className="flex items-start justify-between px-4 py-3 border-b border-border">
        <div className="flex items-center gap-3">
          {preset.icon ? (
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-surface-muted to-muted flex items-center justify-center text-2xl">
              {preset.icon}
            </div>
          ) : (
            <div className={cn('p-2 rounded-lg', categoryInfo.bgColor)}>
              <CategoryIcon className={cn('w-5 h-5', categoryInfo.color)} />
            </div>
          )}
          <div>
            <h3 className="text-sm font-semibold text-fg">{preset.name}</h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className={cn('text-xs px-1.5 py-0.5 rounded', categoryInfo.bgColor, categoryInfo.color)}>
                {categoryInfo.label}
              </span>
            </div>
          </div>
        </div>
        {isSelected && (
          <div className="p-1 bg-info-color rounded-full">
            <Check className="w-3 h-3 text-white" />
          </div>
        )}
      </div>

      {/* Description */}
      <div className="px-4 py-3 border-b border-border">
        <p className="text-xs text-fg-muted line-clamp-2">{preset.description}</p>
      </div>

      {/* Popularity & Estimates */}
      <div className="px-4 py-3 grid grid-cols-3 gap-2 border-b border-border">
        <div className="text-center">
          <div className="flex items-center justify-center gap-0.5 mb-1">
            {Array.from({ length: 5 }).map((_, i) => (
              <Star
                key={i}
                className={cn(
                  'w-3 h-3',
                  i < popularityStars ? 'text-warning-color fill-warning-color' : 'text-muted-foreground'
                )}
              />
            ))}
          </div>
          <span className="text-xs text-fg-muted">{preset.popularity} uses</span>
        </div>
        <div className="text-center border-x border-border">
          <div className="flex items-center justify-center gap-1 mb-1">
            <Clock className="w-3.5 h-3.5 text-info-color" />
            <span className="text-sm font-medium text-fg">~{preset.estimatedSetupTime}m</span>
          </div>
          <span className="text-xs text-fg-muted">Setup time</span>
        </div>
        <div className="text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <DollarSign className="w-3.5 h-3.5 text-success-color" />
            <span className="text-sm font-medium text-fg">${preset.estimatedMonthlyCost}</span>
          </div>
          <span className="text-xs text-fg-muted">/month</span>
        </div>
      </div>

      {/* Stack Configuration */}
      <div className="px-4 py-3 border-b border-border">
        <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">Stack</h4>
        <div className="flex flex-wrap gap-1.5">
          {preset.config.stack.language && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-muted rounded text-xs text-fg">
              <Code className="w-3 h-3" />
              {preset.config.stack.language}
            </span>
          )}
          {preset.config.stack.framework && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-muted rounded text-xs text-fg">
              <Zap className="w-3 h-3" />
              {preset.config.stack.framework}
            </span>
          )}
          {preset.config.stack.frontend && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-muted rounded text-xs text-fg">
              <Globe className="w-3 h-3" />
              {preset.config.stack.frontend}
            </span>
          )}
          {preset.config.stack.backend && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-muted rounded text-xs text-fg">
              <Server className="w-3 h-3" />
              {preset.config.stack.backend}
            </span>
          )}
          {preset.config.stack.database && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-muted rounded text-xs text-fg">
              <Database className="w-3 h-3" />
              {preset.config.stack.database}
            </span>
          )}
        </div>
      </div>

      {/* Included Services */}
      <div className="px-4 py-3 border-b border-border">
        <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">Includes</h4>
        <div className="grid grid-cols-2 gap-1">
          {preset.config.includeRepository && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              Repository
            </div>
          )}
          {preset.config.includeHosting && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              Hosting
            </div>
          )}
          {preset.config.includeDatabase && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              Database
            </div>
          )}
          {preset.config.includeCache && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              Cache
            </div>
          )}
          {preset.config.includeStorage && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              Storage
            </div>
          )}
          {preset.config.includeCICD && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              CI/CD
            </div>
          )}
          {preset.config.includeMonitoring && (
            <div className="flex items-center gap-1.5 text-xs text-fg">
              <Check className="w-3 h-3 text-success-color" />
              Monitoring
            </div>
          )}
        </div>
      </div>

      {/* Features */}
      {preset.features.length > 0 && (
        <div className="px-4 py-3 border-b border-border">
          <h4 className="text-xs font-medium text-fg-muted uppercase tracking-wider mb-2">Features</h4>
          <ul className="space-y-1">
            {preset.features.slice(0, 4).map((feature: string, index: number) => (
              <li key={index} className="flex items-start gap-1.5 text-xs text-fg">
                <Zap className="w-3 h-3 text-warning-color mt-0.5 flex-shrink-0" />
                <span>{feature}</span>
              </li>
            ))}
            {preset.features.length > 4 && (
              <li className="text-xs text-fg-muted">
                +{preset.features.length - 4} more features
              </li>
            )}
          </ul>
        </div>
      )}

      {/* Requirements */}
      {preset.requirements && preset.requirements.length > 0 && (
        <div className="px-4 py-3 border-b border-border bg-warning-bg">
          <h4 className="text-xs font-medium text-warning-color uppercase tracking-wider mb-2 flex items-center gap-1">
            <AlertCircle className="w-3 h-3" />
            Requirements
          </h4>
          <ul className="space-y-1">
            {preset.requirements.map((req: string, index: number) => (
              <li key={index} className="text-xs text-warning-color">
                • {req}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Tags */}
      {preset.tags.length > 0 && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex flex-wrap gap-1">
            {preset.tags.map((tag: string) => (
              <span
                key={tag}
                className="text-xs px-2 py-0.5 bg-muted text-fg-muted rounded-full"
              >
                #{tag}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="px-4 py-3 flex items-center justify-end gap-2">
        {onPreview && (
          <Button variant="ghost" size="sm"
            onClick={onPreview}
            className="px-3 py-1.5 bg-muted hover:opacity-90 rounded-lg text-xs text-fg transition-colors"
          >
            Preview
          </Button>
        )}
        {onSelect && (
          <Button variant="ghost" size="sm"
            onClick={onSelect}
            className={cn(
              'px-3 py-1.5 rounded-lg text-xs transition-colors',
              isSelected
                ? 'bg-success-color text-white'
                : 'bg-info-color hover:opacity-90 text-white'
            )}
          >
            {isSelected ? 'Selected' : 'Use Preset'}
          </Button>
        )}
      </div>
    </div>
  );
}

export default memo(PresetNode);
