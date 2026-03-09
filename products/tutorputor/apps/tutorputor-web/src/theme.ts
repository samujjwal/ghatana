export const cardStyles = {
  base: "bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm",
  interactive:
    "bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm cursor-pointer hover:border-gray-300 dark:hover:border-gray-600 hover:shadow-md transition-all",
  selected: "bg-blue-50 dark:bg-blue-900/30 border-blue-500 dark:border-blue-400",
  padded: "p-4",
  header: "border-b border-gray-200 dark:border-gray-700 p-4",
  body: "p-4",
};

export const textStyles = {
  h1: "text-2xl font-bold text-gray-900 dark:text-white",
  h2: "text-xl font-semibold text-gray-900 dark:text-white",
  h3: "text-lg font-semibold text-gray-900 dark:text-white",
  h4: "text-sm font-medium text-gray-900 dark:text-white",
  body: "text-gray-700 dark:text-gray-300",
  muted: "text-gray-500 dark:text-gray-400",
  small: "text-sm text-gray-600 dark:text-gray-400",
  xs: "text-xs text-gray-500 dark:text-gray-400",
  label: "text-sm font-medium text-gray-700 dark:text-gray-300",
  link: "text-blue-600 dark:text-blue-400 hover:underline",
  mono: "font-mono text-sm text-gray-900 dark:text-white",
};

export const bgStyles = {
  page: "bg-gray-50 dark:bg-gray-900",
  surface: "bg-white dark:bg-gray-800",
  surfaceSecondary: "bg-gray-50 dark:bg-gray-700",
  surfaceTertiary: "bg-gray-100 dark:bg-gray-600",
  code: "bg-gray-100 dark:bg-gray-700",
};

export const borderStyles = {
  default: "border border-gray-200 dark:border-gray-700",
  subtle: "border border-gray-100 dark:border-gray-800",
  divider: "border-gray-200 dark:border-gray-700",
  focus: "focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:focus:ring-blue-400",
};

export const inputStyles = {
  base:
    "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500",
  select:
    "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500",
  textarea:
    "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500",
  search:
    "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500",
};

export const buttonStyles = {
  primary:
    "px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors",
  secondary:
    "px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors",
  danger:
    "px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors",
  success:
    "px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors",
  warning:
    "px-4 py-2 bg-yellow-600 text-white rounded-lg hover:bg-yellow-700 transition-colors",
  ghost:
    "px-4 py-2 text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors",
  sm: "px-3 py-1.5 text-sm",
};

export const badgeStyles = {
  default:
    "px-2 py-1 text-xs font-medium rounded bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300",
  success:
    "px-2 py-1 text-xs font-medium rounded bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200",
  warning:
    "px-2 py-1 text-xs font-medium rounded bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200",
  danger:
    "px-2 py-1 text-xs font-medium rounded bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200",
  info:
    "px-2 py-1 text-xs font-medium rounded bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200",
  purple:
    "px-2 py-1 text-xs font-medium rounded bg-purple-100 dark:bg-purple-900 text-purple-800 dark:text-purple-200",
  tag:
    "px-3 py-1 text-sm rounded-full bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300",
};

export const statusStyles = {
  online: "bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200",
  offline: "bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300",
  degraded: "bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200",
  error: "bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200",
  pending: "bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200",
  processed: "bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200",
};

export const tableStyles = {
  container: "overflow-x-auto",
  table: "w-full",
  thead: "bg-gray-50 dark:bg-gray-700",
  th: "px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider",
  tbody: "divide-y divide-gray-200 dark:divide-gray-700",
  tr: "hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors",
  trSelected: "bg-blue-50 dark:bg-blue-900/30",
  td: "px-4 py-3 text-sm text-gray-900 dark:text-white",
};

export const modalStyles = {
  overlay: "fixed inset-0 bg-black/50 flex items-center justify-center z-50",
  container: "bg-white dark:bg-gray-800 rounded-lg shadow-xl p-6 w-full",
  title: "text-xl font-bold text-gray-900 dark:text-white mb-4",
  closeButton: "text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200",
};

export const toastStyles = {
  base: "fixed bottom-4 right-4 px-4 py-3 rounded-lg shadow-lg text-white",
  success: "bg-green-600",
  error: "bg-red-600",
  info: "bg-blue-600",
  warning: "bg-yellow-600",
};

export const metricCardStyles = {
  base: "bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm",
  green:
    "bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-green-500",
  red: "bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-red-500",
  blue: "bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-blue-500",
  orange:
    "bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-orange-500",
  purple:
    "bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-purple-500",
};

export function cn(
  ...classes: (string | undefined | null | false)[]
): string {
  return classes.filter(Boolean).join(" ");
}

export function getCardClasses(options: {
  selected?: boolean;
  interactive?: boolean;
  padded?: boolean;
}): string {
  const { selected, interactive, padded } = options;

  const classes = [
    interactive ? cardStyles.interactive : cardStyles.base,
    selected && cardStyles.selected,
    padded && cardStyles.padded,
  ];

  return cn(...classes);
}

export function getStatusClasses(
  status:
    | "online"
    | "offline"
    | "degraded"
    | "error"
    | "pending"
    | "processed"
    | "active"
    | "inactive"
): string {
  const statusMap: Record<string, string> = {
    online: statusStyles.online,
    offline: statusStyles.offline,
    degraded: statusStyles.degraded,
    error: statusStyles.error,
    pending: statusStyles.pending,
    processed: statusStyles.processed,
    active: statusStyles.online,
    inactive: statusStyles.offline,
  };

  return `px-2 py-1 rounded-full text-xs font-medium ${
    statusMap[status] || statusStyles.offline
  }`;
}
