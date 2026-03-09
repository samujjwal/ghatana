import { Link } from "react-router-dom";
import { cardStyles, textStyles, cn } from "../theme";

export type StatCardColor =
  | "gray"
  | "blue"
  | "green"
  | "yellow"
  | "red"
  | "purple"
  | "orange";

export interface StatCardProps {
  title: string;
  value: string | number;
  color?: StatCardColor;
  href?: string;
  onClick?: () => void;
  borderColor?: string;
}

export function StatCard({
  title,
  value,
  color = "gray",
  href,
  onClick,
  borderColor,
}: StatCardProps) {
  const colorClasses: Record<StatCardColor, string> = {
    gray: "text-gray-900 dark:text-white",
    blue: "text-blue-600 dark:text-blue-400",
    green: "text-green-600 dark:text-green-400",
    yellow: "text-yellow-600 dark:text-yellow-400",
    red: "text-red-600 dark:text-red-400",
    purple: "text-purple-600 dark:text-purple-400",
    orange: "text-orange-600 dark:text-orange-400",
  };

  const content = (
    <>
      <p className={textStyles.small}>{title}</p>
      <p className={cn("text-2xl font-bold", colorClasses[color])}>{value}</p>
    </>
  );

  const baseClass = cn(
    cardStyles.base,
    "p-4",
    borderColor && `border-l-4 ${borderColor}`,
    (href || onClick) &&
      "cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors",
  );

  if (href) {
    return (
      <Link to={href} className={baseClass}>
        {content}
      </Link>
    );
  }

  if (onClick) {
    return (
      <div onClick={onClick} className={baseClass}>
        {content}
      </div>
    );
  }

  return (
    <div className={cn(cardStyles.base, "p-4", borderColor && `border-l-4 ${borderColor}`)}>
      {content}
    </div>
  );
}
