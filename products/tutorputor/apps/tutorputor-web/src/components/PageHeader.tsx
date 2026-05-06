import { useNavigate } from "react-router-dom";
import type { ReactNode } from "react";
import { PageHeader as SharedPageHeader } from "@ghatana/product-shell";

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
  const eyebrow = backTo ? (
    <button
      type="button"
      onClick={() => navigate(backTo)}
      className="inline-flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
    >
      <span>←</span>
      <span>{backLabel || "Back"}</span>
    </button>
  ) : undefined;

  return (
    <SharedPageHeader
      title={title}
      description={description}
      eyebrow={eyebrow}
      actions={actions}
      className="mb-6"
    />
  );
}
