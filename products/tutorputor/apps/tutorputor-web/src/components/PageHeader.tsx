import { useNavigate } from "react-router-dom";
import type { ReactNode } from "react";
import { cn, textStyles } from "../theme";

interface PageHeaderProps {
  title: string;
  description?: string;
  backTo?: string;
  backLabel?: string;
  actions?: ReactNode;
}

export function PageHeader({
  title,
  description,
  backTo,
  backLabel,
  actions,
}: PageHeaderProps) {
  const navigate = useNavigate();

  return (
    <div className="flex justify-between items-start mb-6">
      <div>
        {backTo && (
          <button
            type="button"
            onClick={() => navigate(backTo)}
            className="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 mb-2 transition-colors"
          >
            <span>←</span>
            <span>{backLabel || "Back"}</span>
          </button>
        )}
        <h1 className={textStyles.h1}>{title}</h1>
        {description && (
          <p className={cn(textStyles.muted, "mt-1")}>{description}</p>
        )}
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  );
}
