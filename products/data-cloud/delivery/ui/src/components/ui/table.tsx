import type {
  HTMLAttributes,
  TdHTMLAttributes,
  ThHTMLAttributes,
  ReactNode,
} from "react";

export function Table({
  className = "",
  ...props
}: HTMLAttributes<HTMLTableElement>): ReactNode {
  return (
    <div className="w-full overflow-auto">
      <table
        className={`w-full caption-bottom text-sm ${className}`}
        {...props}
      />
    </div>
  );
}

export function TableHeader(
  props: HTMLAttributes<HTMLTableSectionElement>,
): ReactNode {
  return <thead {...props} />;
}

export function TableBody(
  props: HTMLAttributes<HTMLTableSectionElement>,
): ReactNode {
  return <tbody {...props} />;
}

export function TableRow({
  className = "",
  ...props
}: HTMLAttributes<HTMLTableRowElement>): ReactNode {
  return <tr className={`border-b border-gray-100 ${className}`} {...props} />;
}

export function TableHead({
  className = "",
  ...props
}: ThHTMLAttributes<HTMLTableCellElement>): ReactNode {
  return (
    <th
      className={`h-10 px-3 text-left align-middle font-medium text-gray-600 ${className}`}
      {...props}
    />
  );
}

export function TableCell({
  className = "",
  ...props
}: TdHTMLAttributes<HTMLTableCellElement>): ReactNode {
  return <td className={`px-3 py-3 align-middle ${className}`} {...props} />;
}
