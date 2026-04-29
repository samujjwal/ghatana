import React from "react";

export interface AlertDialogProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  children?: React.ReactNode;
}

export interface AlertDialogContentProps {
  className?: string;
  children?: React.ReactNode;
}

export interface AlertDialogHeaderProps {
  children?: React.ReactNode;
  className?: string;
}

export interface AlertDialogFooterProps {
  children?: React.ReactNode;
  className?: string;
}

export interface AlertDialogTitleProps {
  children?: React.ReactNode;
  className?: string;
}

export interface AlertDialogDescriptionProps {
  children?: React.ReactNode;
  className?: string;
}

export interface AlertDialogActionProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children?: React.ReactNode;
}

export interface AlertDialogCancelProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children?: React.ReactNode;
}

function AlertDialogRoot({ open, onOpenChange, children }: AlertDialogProps) {
  if (!open) return null;
  return (
    <div role="alertdialog" aria-modal="true" style={{ position: "fixed", inset: 0, zIndex: 50, display: "flex", alignItems: "center", justifyContent: "center" }}>
      <div onClick={() => onOpenChange?.(false)} style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.5)" }} />
      <div style={{ position: "relative", zIndex: 1 }}>{children}</div>
    </div>
  );
}

AlertDialogRoot.Content = function AlertDialogContent({ className, children }: AlertDialogContentProps) {
  return <div className={className}>{children}</div>;
};
AlertDialogRoot.Header = function AlertDialogHeader({ children, className }: AlertDialogHeaderProps) {
  return <div className={className}>{children}</div>;
};
AlertDialogRoot.Footer = function AlertDialogFooter({ children, className }: AlertDialogFooterProps) {
  return <div className={className}>{children}</div>;
};
AlertDialogRoot.Title = function AlertDialogTitle({ children, className }: AlertDialogTitleProps) {
  return <h2 className={className}>{children}</h2>;
};
AlertDialogRoot.Description = function AlertDialogDescription({ children, className }: AlertDialogDescriptionProps) {
  return <p className={className}>{children}</p>;
};
AlertDialogRoot.Action = function AlertDialogAction({ children, ...props }: AlertDialogActionProps) {
  return <button type="button" {...props}>{children}</button>;
};
AlertDialogRoot.Cancel = function AlertDialogCancel({ children, ...props }: AlertDialogCancelProps) {
  return <button type="button" {...props}>{children}</button>;
};

export const AlertDialog = AlertDialogRoot;
export default AlertDialog;
