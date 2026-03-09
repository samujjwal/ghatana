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
import type { NodeProps } from '@xyflow/react';
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
import type { Preset, PresetCategory, PresetConfig } from '@ghatana/yappc-api';

export interface PresetNodeData {
  preset: Preset;
  isSelected?: boolean;
  onSelect?: () => void;
  onPreview?: () => void;
}

const categoryConfig: Record<
  PresetCategory,
  { color: string; bgColor: string; icon: typeof Layers; label: string }
> = {
  STARTER: { color: 'text-green-400', bgColor: 'bg-green-500/20', icon: Rocket, label: 'Starter' },
  FULL_STACK: { color: 'text-blue-400', bgColor: 'bg-blue-500/20', icon: Layers, label: 'Full Stack' },
  API_ONLY: { color: 'text-purple-400', bgColor: 'bg-purple-500/20', icon: Server, label: 'API Only' },
  STATIC_SITE: { color: 'text-cyan-400', bgColor: 'bg-cyan-500/20', icon: Layout, label: 'Static Site' },
  MONOREPO: { color: 'text-orange-400', bgColor: 'bg-orange-500/20', icon: Box, label: 'Monorepo' },
  MICROSERVICES: { color: 'text-pink-400', bgColor: 'bg-pink-500/20', icon: Grid3X3, label: 'Microservices' },
  CUSTOM: { color: 'text-gray-400', bgColor: 'bg-gray-500/20', icon: Package, label: 'Custom' },
};

function PresetNode({ data }: NodeProps<PresetNodeData>) {
  const { preset, isSelected, onSelect, onPreview } = data;
  const categoryInfo = categoryConfig[preset.category];
  const CategoryIcon = categoryInfo.icon;

  const popularityStars = Math.min(5, Math.ceil(preset.popularity / 200));

  return (
    <div
      className={cn(
        'bg-slate-800 rounded-lg border shadow-xl min-w-[340px] max-w-[400px] transition-all',
        isSelected ? 'border-blue-500 ring-2 ring-blue-500/30' : 'border-slate-700'
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-purple-500" />
      <Handle type="source" position={Position.Right} className="!bg-purple-500" />

      {/* Header */}
      <div className="flex items-start justify-between px-4 py-3 border-b border-slate-700">
        <div className="flex items-center gap-3">
          {preset.icon ? (
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-slate-700 to-slate-600 flex items-center justify-center text-2xl">
              {preset.icon}
            </div>
          ) : (
            <div className={cn('p-2 rounded-lg', categoryInfo.bgColor)}>
              <CategoryIcon className={cn('w-5 h-5', categoryInfo.color)} />
            </div>
          )}
          <div>
            <h3 className="text-sm font-semibold text-white">{preset.name}</h3>
            <div className="flex items-center gap-2 mt-0.5">
              <span className={cn('text-xs px-1.5 py-0.5 rounded', categoryInfo.bgColor, categoryInfo.color)}>
                {categoryInfo.label}
              </span>
            </div>
          </div>
        </div>
        {isSelected && (
          <div className="p-1 bg-blue-500 rounded-full">
            <Check className="w-3 h-3 text-white" />
          </div>
        )}
      </div>

      {/* Description */}
      <div className="px-4 py-3 border-b border-slate-700">
        <p className="text-xs text-slate-400 line-clamp-2">{preset.description}</p>
      </div>

      {/* Popularity & Estimates */}
      <div className="px-4 py-3 grid grid-cols-3 gap-2 border-b border-slate-700">
        <div className="text-center">
          <div className="flex items-center justify-center gap-0.5 mb-1">
            {Array.from({ length: 5 }).map((_, i) => (
              <Star
                key={i}
                className={cn(
                  'w-3 h-3',
                  i < popularityStars ? 'text-yellow-400 fill-yellow-400' : 'text-slate-600'
                )}
              />
            ))}
          </div>
          <span className="text-xs text-slate-400">{preset.popularity} uses</span>
        </div>
        <div className="text-center border-x border-slate-700">
          <div className="flex items-center justify-center gap-1 mb-1">
            <Clock className="w-3.5 h-3.5 text-blue-400" />
            <span className="text-sm font-medium text-white">~{preset.estimatedSetupTime}m</span>
          </div>
          <span className="text-xs text-slate-400">Setup time</span>
        </div>
        <div className="text-center">
          <div className="flex items-center justify-center gap-1 mb-1">
            <DollarSign className="w-3.5 h-3.5 text-green-400" />
            <span className="text-sm font-medium text-white">${preset.estimatedMonthlyCost}</span>
          </div>
          <span className="text-xs text-slate-400">/month</span>
        </div>
      </div>

      {/* Stack Configuration */}
      <div className="px-4 py-3 border-b border-slate-700">
        <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">Stack</h4>
        <div className="flex flex-wrap gap-1.5">
          {preset.config.stack.language && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-slate-700 rounded text-xs text-slate-300">
              <Code className="w-3 h-3" />
              {preset.config.stack.language}
            </span>
          )}
          {preset.config.stack.framework && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-slate-700 rounded text-xs text-slate-300">
              <Zap className="w-3 h-3" />
              {preset.config.stack.framework}
            </span>
          )}
          {preset.config.stack.frontend && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-slate-700 rounded text-xs text-slate-300">
              <Globe className="w-3 h-3" />
              {preset.config.stack.frontend}
            </span>
          )}
          {preset.config.stack.backend && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-slate-700 rounded text-xs text-slate-300">
              <Server className="w-3 h-3" />
              {preset.config.stack.backend}
            </span>
          )}
          {preset.config.stack.database && (
            <span className="inline-flex items-center gap-1 px-2 py-1 bg-slate-700 rounded text-xs text-slate-300">
              <Database className="w-3 h-3" />
              {preset.config.stack.database}
            </span>
          )}
        </div>
      </div>

      {/* Included Services */}
      <div className="px-4 py-3 border-b border-slate-700">
        <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">Includes</h4>
        <div className="grid grid-cols-2 gap-1">
          {preset.config.includeRepository && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              Repository
            </div>
          )}
          {preset.config.includeHosting && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              Hosting
            </div>
          )}
          {preset.config.includeDatabase && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              Database
            </div>
          )}
          {preset.config.includeCache && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              Cache
            </div>
          )}
          {preset.config.includeStorage && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              Storage
            </div>
          )}
          {preset.config.includeCICD && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              CI/CD
            </div>
          )}
          {preset.config.includeMonitoring && (
            <div className="flex items-center gap-1.5 text-xs text-slate-300">
              <Check className="w-3 h-3 text-green-400" />
              Monitoring
            </div>
          )}
        </div>
      </div>

      {/* Features */}
      {preset.features.length > 0 && (
        <div className="px-4 py-3 border-b border-slate-700">
          <h4 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2">Features</h4>
          <ul className="space-y-1">
            {preset.features.slice(0, 4).map((feature, index) => (
              <li key={index} className="flex items-start gap-1.5 text-xs text-slate-300">
                <Zap className="w-3 h-3 text-yellow-400 mt-0.5 flex-shrink-0" />
                <span>{feature}</span>
              </li>
            ))}
            {preset.features.length > 4 && (
              <li className="text-xs text-slate-500">
                +{preset.features.length - 4} more features
              </li>
            )}
          </ul>
        </div>
      )}

      {/* Requirements */}
      {preset.requirements && preset.requirements.length > 0 && (
        <div className="px-4 py-3 border-b border-slate-700 bg-amber-500/5">
          <h4 className="text-xs font-medium text-amber-400 uppercase tracking-wider mb-2 flex items-center gap-1">
            <AlertCircle className="w-3 h-3" />
            Requirements
          </h4>
          <ul className="space-y-1">
            {preset.requirements.map((req, index) => (
              <li key={index} className="text-xs text-amber-200/80">
                • {req}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Tags */}
      {preset.tags.length > 0 && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex flex-wrap gap-1">
            {preset.tags.map((tag) => (
              <span
                key={tag}
                className="text-xs px-2 py-0.5 bg-slate-700/50 text-slate-400 rounded-full"
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
          <button
            onClick={onPreview}
            className="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 rounded-lg text-xs text-slate-300 transition-colors"
          >
            Preview
          </button>
        )}
        {onSelect && (
          <button
            onClick={onSelect}
            className={cn(
              'px-3 py-1.5 rounded-lg text-xs transition-colors',
              isSelected
                ? 'bg-green-600 text-white'
                : 'bg-blue-600 hover:bg-blue-500 text-white'
            )}
          >
            {isSelected ? 'Selected' : 'Use Preset'}
          </button>
        )}
      </div>
    </div>
  );
}

export default memo(PresetNode);
