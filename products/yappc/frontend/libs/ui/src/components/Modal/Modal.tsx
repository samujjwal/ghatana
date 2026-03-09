import { useEffect, useRef } from 'react';

import type { ReactNode } from 'react';

/**
 *
 */
export interface ModalProps {
  /**
   * Whether the modal is open
   */
  open: boolean;
  
  /**
   * Callback when modal should close
   */
  onClose: () => void;
  
  /**
   * Modal title
   */
  title?: string;
  
  /**
   * Modal content
   */
  children: ReactNode;
  
  /**
   * Modal size
   */
  size?: 'small' | 'medium' | 'large' | 'fullscreen';
  
  /**
   * Whether clicking backdrop closes modal
   */
  closeOnBackdropClick?: boolean;
  
  /**
   * Whether pressing Escape closes modal
   */
  closeOnEscape?: boolean;
  
  /**
   * Footer content
   */
  footer?: ReactNode;
  
  /**
   * Additional class name
   */
  className?: string;
}

/**
 * Modal component for displaying content in an overlay
 * 
 * @example
 * ```tsx
 * <Modal
 *   open={isOpen}
 *   onClose={() => setIsOpen(false)}
 *   title="Confirm Action"
 *   footer={
 *     <>
 *       <Button onClick={() => setIsOpen(false)}>Cancel</Button>
 *       <Button variant="contained" onClick={handleConfirm}>Confirm</Button>
 *     </>
 *   }
 * >
 *   Are you sure you want to proceed?
 * </Modal>
 * ```
 */
export function Modal({
  open,
  onClose,
  title,
  children,
  size = 'medium',
  closeOnBackdropClick = true,
  closeOnEscape = true,
  footer,
  className = '',
}: ModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

    const handleEscape = (e: KeyboardEvent) => {
      if (closeOnEscape && e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscape);
    document.body.style.overflow = 'hidden';

    // Focus trap
    const focusableElements = modalRef.current?.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    const firstElement = focusableElements?.[0] as HTMLElement;
    firstElement?.focus();

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = '';
    };
  }, [open, closeOnEscape, onClose]);

  if (!open) return null;

  const sizeMap = {
    small: '400px',
    medium: '600px',
    large: '800px',
    fullscreen: '100vw',
  };

  const backdropStyle: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1300,
    padding: size === 'fullscreen' ? 0 : '1rem',
  };

  const modalStyle: React.CSSProperties = {
    backgroundColor: 'var(--color-background-paper, #ffffff)',
    borderRadius: size === 'fullscreen' ? 0 : '0.5rem',
    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
    maxWidth: sizeMap[size],
    width: '100%',
    maxHeight: size === 'fullscreen' ? '100vh' : 'calc(100vh - 2rem)',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  };

  const headerStyle: React.CSSProperties = {
    padding: '1.5rem',
    borderBottom: '1px solid var(--color-grey-300, #e0e0e0)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  };

  const titleStyle: React.CSSProperties = {
    fontSize: '1.25rem',
    fontWeight: 600,
    color: 'var(--color-text-primary, #212121)',
    margin: 0,
  };

  const closeButtonStyle: React.CSSProperties = {
    background: 'none',
    border: 'none',
    fontSize: '1.5rem',
    cursor: 'pointer',
    padding: '0.25rem',
    color: 'var(--color-text-secondary, #757575)',
    lineHeight: 1,
  };

  const contentStyle: React.CSSProperties = {
    padding: '1.5rem',
    overflowY: 'auto',
    flex: 1,
  };

  const footerStyle: React.CSSProperties = {
    padding: '1rem 1.5rem',
    borderTop: '1px solid var(--color-grey-300, #e0e0e0)',
    display: 'flex',
    gap: '0.75rem',
    justifyContent: 'flex-end',
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (closeOnBackdropClick && e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div
      style={backdropStyle}
      onClick={handleBackdropClick}
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? 'modal-title' : undefined}
    >
      <div
        ref={modalRef}
        style={modalStyle}
        className={className}
      >
        {title && (
          <div style={headerStyle}>
            <h2 id="modal-title" style={titleStyle}>
              {title}
            </h2>
            <button
              onClick={onClose}
              style={closeButtonStyle}
              aria-label="Close modal"
            >
              ×
            </button>
          </div>
        )}
        
        <div style={contentStyle}>
          {children}
        </div>
        
        {footer && (
          <div style={footerStyle}>
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
