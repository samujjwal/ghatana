/**
 * SimulationTemplateCard Component
 *
 * Card component for displaying a simulation template in the marketplace.
 *
 * @doc.type component
 * @doc.purpose Display template preview with stats and actions
 * @doc.layer product
 * @doc.pattern Card
 */

import { useState, useCallback } from "react";
import { Badge, Button, Tooltip } from "@ghatana/ui";
import type { SimulationTemplate, TemplateDifficulty } from "../types";

// =============================================================================
// Props
// =============================================================================

export interface SimulationTemplateCardProps {
  template: SimulationTemplate;
  onSelect?: (template: SimulationTemplate) => void;
  onFavorite?: (templateId: string) => void;
  onUse?: (templateId: string) => void;
  isFavorited?: boolean;
  isLoading?: boolean;
  compact?: boolean;
}

// =============================================================================
// Helper Functions
// =============================================================================

const DIFFICULTY_CONFIG: Record<
  TemplateDifficulty,
  { label: string; tone: "success" | "info" | "warning" | "danger" }
> = {
  beginner: { label: "Beginner", tone: "success" },
  intermediate: { label: "Intermediate", tone: "info" },
  advanced: { label: "Advanced", tone: "warning" },
  expert: { label: "Expert", tone: "danger" },
};

const DOMAIN_ICONS: Record<string, string> = {
  PHYSICS: "⚛️",
  CHEMISTRY: "🧪",
  BIOLOGY: "🧬",
  MEDICINE: "💊",
  ECONOMICS: "📊",
  CS_DISCRETE: "🔢",
  MATHEMATICS: "📐",
  ENGINEERING: "⚙️",
};

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`;
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`;
  }
  return num.toString();
}

function renderStars(rating: number): string {
  const fullStars = Math.floor(rating);
  const hasHalfStar = rating - fullStars >= 0.5;
  let stars = "★".repeat(fullStars);
  if (hasHalfStar) stars += "☆";
  return stars.padEnd(5, "☆");
}

// =============================================================================
// Component
// =============================================================================

export const SimulationTemplateCard = ({
  template,
  onSelect,
  onFavorite,
  onUse,
  isFavorited = false,
  isLoading = false,
  compact = false,
}: SimulationTemplateCardProps) => {
  const [isHovered, setIsHovered] = useState(false);

  const handleClick = useCallback(() => {
    onSelect?.(template);
  }, [template, onSelect]);

  const handleFavorite = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onFavorite?.(template.id);
    },
    [template.id, onFavorite]
  );

  const handleUse = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onUse?.(template.id);
    },
    [template.id, onUse]
  );

  const difficultyConfig = DIFFICULTY_CONFIG[template.difficulty];
  const domainIcon = DOMAIN_ICONS[template.domain] ?? "📦";

  return (
    <div
      className={`
        relative group rounded-xl border border-gray-200 dark:border-gray-700
        bg-white dark:bg-gray-800 overflow-hidden cursor-pointer
        transition-all duration-200 hover:shadow-lg hover:border-blue-300
        dark:hover:border-blue-600
        ${compact ? "h-48" : "h-72"}
        ${isLoading ? "opacity-50 pointer-events-none" : ""}
      `}
      onClick={handleClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {/* Thumbnail */}
      <div className={`relative ${compact ? "h-24" : "h-36"} overflow-hidden bg-gray-100 dark:bg-gray-900`}>
        {template.thumbnailUrl ? (
          <img
            src={template.thumbnailUrl}
            alt={template.title}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-4xl bg-gradient-to-br from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20">
            {domainIcon}
          </div>
        )}

        {/* Badges Overlay */}
        <div className="absolute top-2 left-2 flex flex-wrap gap-1">
          {template.isVerified && (
            <Tooltip content="Verified by TutorPutor">
              <Badge variant="solid" tone="primary">
                ✓ Verified
              </Badge>
            </Tooltip>
          )}
          {template.isPremium && (
            <Badge variant="solid" tone="warning">
              Premium
            </Badge>
          )}
        </div>

        {/* Favorite Button */}
        <button
          onClick={handleFavorite}
          className={`
            absolute top-2 right-2 w-8 h-8 rounded-full flex items-center justify-center
            transition-all duration-200
            ${
              isFavorited
                ? "bg-red-500 text-white"
                : "bg-white/80 dark:bg-gray-800/80 text-gray-500 hover:text-red-500"
            }
          `}
        >
          {isFavorited ? "❤️" : "🤍"}
        </button>

        {/* Hover Actions */}
        <div
          className={`
            absolute inset-0 bg-black/50 flex items-center justify-center
            transition-opacity duration-200
            ${isHovered ? "opacity-100" : "opacity-0"}
          `}
        >
          <Button
            variant="solid"
            tone="primary"
            size="sm"
            onClick={handleUse}
          >
            Use Template
          </Button>
        </div>
      </div>

      {/* Content */}
      <div className={`p-3 ${compact ? "space-y-1" : "space-y-2"}`}>
        {/* Domain & Difficulty */}
        <div className="flex items-center gap-2">
          <span className="text-sm">{domainIcon}</span>
          <Badge variant="soft" tone={difficultyConfig.tone}>
            {difficultyConfig.label}
          </Badge>
        </div>

        {/* Title */}
        <h3 className={`font-semibold text-gray-900 dark:text-white line-clamp-1 ${compact ? "text-sm" : "text-base"}`}>
          {template.title}
        </h3>

        {/* Description - only in non-compact mode */}
        {!compact && (
          <p className="text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
            {template.description}
          </p>
        )}

        {/* Stats */}
        <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
          <div className="flex items-center gap-3">
            <span title="Rating" className="text-yellow-500">
              {renderStars(template.stats.rating)}
              <span className="ml-1 text-gray-500">({template.stats.ratingCount})</span>
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span title="Uses">📥 {formatNumber(template.stats.uses)}</span>
          </div>
        </div>

        {/* Author - only in non-compact mode */}
        {!compact && (
          <div className="flex items-center gap-2 pt-1 border-t border-gray-100 dark:border-gray-700">
            {template.author.avatarUrl ? (
              <img
                src={template.author.avatarUrl}
                alt={template.author.name}
                className="w-5 h-5 rounded-full"
              />
            ) : (
              <div className="w-5 h-5 rounded-full bg-gray-200 dark:bg-gray-600 flex items-center justify-center text-xs">
                {template.author.name.charAt(0)}
              </div>
            )}
            <span className="text-xs text-gray-600 dark:text-gray-400 truncate">
              {template.author.name}
              {template.author.isVerified && (
                <span className="ml-1 text-blue-500">✓</span>
              )}
            </span>
          </div>
        )}
      </div>
    </div>
  );
};

export default SimulationTemplateCard;
