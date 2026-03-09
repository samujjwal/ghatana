/**
 * Template Card Component
 *
 * @description Displays a project template with preview, description, and
 * quick start action. Supports popular, featured, and custom templates.
 *
 * @doc.type component
 * @doc.purpose Template selection card
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

import React, { useState } from 'react';
import { motion } from 'framer-motion';
import {
  Play,
  Eye,
  Star,
  Download,
  Clock,
  Users,
  GitBranch,
  Box,
  Layers,
  Code2,
  Globe,
  Server,
  Smartphone,
  Database,
  Shield,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Avatar } from '@ghatana/ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';

// =============================================================================
// Types
// =============================================================================

export type TemplateCategory = 
  | 'web' 
  | 'mobile' 
  | 'backend' 
  | 'fullstack' 
  | 'microservices' 
  | 'data' 
  | 'ml' 
  | 'devops' 
  | 'custom';

export interface TemplateAuthor {
  id: string;
  name: string;
  avatar?: string;
  verified?: boolean;
}

export interface Template {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  thumbnail?: string;
  author: TemplateAuthor;
  stars?: number;
  downloads?: number;
  createdAt: string;
  updatedAt?: string;
  tags?: string[];
  features?: string[];
  techStack?: string[];
  isPopular?: boolean;
  isFeatured?: boolean;
  isNew?: boolean;
  estimatedTime?: string;
}

export interface TemplateCardProps {
  /** Template data */
  template: Template;
  /** Called when template is selected to start */
  onSelect: () => void;
  /** Called when preview is requested */
  onPreview?: () => void;
  /** Selected state */
  selected?: boolean;
  /** Compact display mode */
  compact?: boolean;
  /** Show tech stack */
  showTechStack?: boolean;
  /** Show author info */
  showAuthor?: boolean;
  /** Additional CSS classes */
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const CATEGORY_CONFIG: Record<TemplateCategory, {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  color: string;
}> = {
  web: { icon: Globe, label: 'Web App', color: 'text-blue-500' },
  mobile: { icon: Smartphone, label: 'Mobile', color: 'text-purple-500' },
  backend: { icon: Server, label: 'Backend', color: 'text-green-500' },
  fullstack: { icon: Layers, label: 'Full Stack', color: 'text-indigo-500' },
  microservices: { icon: GitBranch, label: 'Microservices', color: 'text-cyan-500' },
  data: { icon: Database, label: 'Data', color: 'text-amber-500' },
  ml: { icon: Box, label: 'ML/AI', color: 'text-pink-500' },
  devops: { icon: Shield, label: 'DevOps', color: 'text-orange-500' },
  custom: { icon: Code2, label: 'Custom', color: 'text-neutral-500' },
};

// =============================================================================
// Animation Variants
// =============================================================================

const cardVariants = {
  hidden: { opacity: 0, scale: 0.95 },
  visible: { opacity: 1, scale: 1 },
  hover: { scale: 1.02 },
  tap: { scale: 0.98 },
} as const;

// =============================================================================
// Main Component
// =============================================================================

export const TemplateCard: React.FC<TemplateCardProps> = ({
  template,
  onSelect,
  onPreview,
  selected = false,
  compact = false,
  showTechStack = true,
  showAuthor = true,
  className,
}) => {
  const [imageLoaded, setImageLoaded] = useState(false);
  const categoryConfig = CATEGORY_CONFIG[template.category];
  const CategoryIcon = categoryConfig.icon;

  // Compact card
  if (compact) {
    return (
      <motion.button
        variants={cardVariants}
        initial="hidden"
        animate="visible"
        whileHover="hover"
        whileTap="tap"
        onClick={onSelect}
        className={cn(
          'group flex items-center gap-3 rounded-lg border p-3 text-left transition-all',
          'hover:border-primary-300 hover:bg-primary-50/50',
          'dark:border-neutral-700 dark:hover:border-primary-700 dark:hover:bg-primary-950/20',
          selected && 'border-primary-500 bg-primary-50 dark:border-primary-500 dark:bg-primary-950/30',
          className
        )}
      >
        <div className={cn(
          'flex h-10 w-10 items-center justify-center rounded-lg',
          'bg-neutral-100 dark:bg-neutral-800',
          categoryConfig.color
        )}>
          <CategoryIcon className="h-5 w-5" />
        </div>
        
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-medium text-neutral-900 dark:text-neutral-100 truncate">
              {template.name}
            </span>
            {template.isPopular && (
              <Badge variant="solid" colorScheme="amber" className="text-xs">
                Popular
              </Badge>
            )}
          </div>
          <p className="text-xs text-neutral-500 truncate">
            {template.description}
          </p>
        </div>

        {selected && (
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            className="flex h-6 w-6 items-center justify-center rounded-full bg-primary-500 text-white"
          >
            <Star className="h-3 w-3 fill-current" />
          </motion.div>
        )}
      </motion.button>
    );
  }

  // Full card
  return (
    <motion.div
      variants={cardVariants}
      initial="hidden"
      animate="visible"
      whileHover="hover"
      className={cn(
        'group relative overflow-hidden rounded-lg border bg-white transition-all',
        'hover:shadow-lg',
        'dark:border-neutral-700 dark:bg-neutral-900',
        selected && 'ring-2 ring-primary-500',
        className
      )}
    >
      {/* Thumbnail/Preview */}
      <div className="relative aspect-video bg-neutral-100 dark:bg-neutral-800 overflow-hidden">
        {template.thumbnail ? (
          <>
            {!imageLoaded && (
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="h-8 w-8 animate-pulse rounded-full bg-neutral-300 dark:bg-neutral-600" />
              </div>
            )}
            <img
              src={template.thumbnail}
              alt={template.name}
              className={cn(
                'h-full w-full object-cover transition-opacity duration-300',
                imageLoaded ? 'opacity-100' : 'opacity-0'
              )}
              onLoad={() => setImageLoaded(true)}
            />
          </>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center">
            <CategoryIcon className={cn('h-16 w-16 opacity-50', categoryConfig.color)} />
          </div>
        )}

        {/* Badges */}
        <div className="absolute top-2 left-2 flex gap-1">
          {template.isFeatured && (
            <Badge variant="solid" colorScheme="primary" className="text-xs">
              Featured
            </Badge>
          )}
          {template.isNew && (
            <Badge variant="solid" colorScheme="success" className="text-xs">
              New
            </Badge>
          )}
          {template.isPopular && !template.isFeatured && (
            <Badge variant="solid" colorScheme="amber" className="text-xs">
              Popular
            </Badge>
          )}
        </div>

        {/* Preview button */}
        {onPreview && (
          <motion.div
            initial={{ opacity: 0 }}
            whileHover={{ opacity: 1 }}
            className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <Button variant="solid" onClick={(e: React.MouseEvent) => {
              e.stopPropagation();
              onPreview();
            }}>
              <Eye className="mr-2 h-4 w-4" />
              Preview
            </Button>
          </motion.div>
        )}
      </div>

      {/* Content */}
      <div className="p-4">
        {/* Category */}
        <div className="flex items-center gap-2 mb-2">
          <Badge variant="outline" className={cn('text-xs', categoryConfig.color)}>
            <CategoryIcon className="mr-1 h-3 w-3" />
            {categoryConfig.label}
          </Badge>
          {template.estimatedTime && (
            <span className="flex items-center gap-1 text-xs text-neutral-500">
              <Clock className="h-3 w-3" />
              {template.estimatedTime}
            </span>
          )}
        </div>

        {/* Title & Description */}
        <h3 className="font-semibold text-neutral-900 dark:text-neutral-100">
          {template.name}
        </h3>
        <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400 line-clamp-2">
          {template.description}
        </p>

        {/* Tech stack */}
        {showTechStack && template.techStack && template.techStack.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1">
            {template.techStack.slice(0, 4).map((tech) => (
              <Badge key={tech} variant="outline" className="text-xs">
                {tech}
              </Badge>
            ))}
            {template.techStack.length > 4 && (
              <Badge variant="outline" className="text-xs">
                +{template.techStack.length - 4}
              </Badge>
            )}
          </div>
        )}

        {/* Footer */}
        <div className="mt-4 flex items-center justify-between">
          {/* Author */}
          {showAuthor && (
            <div className="flex items-center gap-2">
              <Avatar size="small" alt={template.author.name} src={template.author.avatar} />
              <div className="flex items-center gap-1">
                <span className="text-sm text-neutral-600 dark:text-neutral-400">
                  {template.author.name}
                </span>
                {template.author.verified && (
                  <Tooltip>
                    <TooltipTrigger>
                      <Shield className="h-3 w-3 text-primary-500" />
                    </TooltipTrigger>
                    <TooltipContent>Verified author</TooltipContent>
                  </Tooltip>
                )}
              </div>
            </div>
          )}

          {/* Stats */}
          <div className="flex items-center gap-3 text-xs text-neutral-500">
            {template.stars !== undefined && (
              <Tooltip>
                <TooltipTrigger className="flex items-center gap-1">
                  <Star className="h-3 w-3" />
                  {template.stars.toLocaleString()}
                </TooltipTrigger>
                <TooltipContent>{template.stars} stars</TooltipContent>
              </Tooltip>
            )}
            {template.downloads !== undefined && (
              <Tooltip>
                <TooltipTrigger className="flex items-center gap-1">
                  <Download className="h-3 w-3" />
                  {template.downloads.toLocaleString()}
                </TooltipTrigger>
                <TooltipContent>{template.downloads} downloads</TooltipContent>
              </Tooltip>
            )}
          </div>
        </div>

        {/* Action button */}
        <Button
          variant={selected ? 'solid' : 'outline'}
          colorScheme="primary"
          className="w-full mt-4"
          onClick={onSelect}
        >
          <Play className="mr-2 h-4 w-4" />
          {selected ? 'Selected' : 'Use Template'}
        </Button>
      </div>
    </motion.div>
  );
};

export default TemplateCard;
