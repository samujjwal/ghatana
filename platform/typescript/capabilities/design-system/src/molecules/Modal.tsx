import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { cn } from '@ghatana/utils';
import {
  lightColors,
  darkColors,
  lightShadows,
  darkShadows,
  componentRadius,
  fontSize,
  fontWeight,
  zIndex,
} from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

import { IconButton } from '../atoms/IconButton';
import { useMergeRefs } from '../hooks/useMergeRefs';

type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

const sizeMap: Record<ModalSize, string> = {
  sm: '400px',
  md: '520px',
  lg: '720px',
  xl: '960px',
};

function getFocusableElements(root: HTMLElement): HTMLElement[] {
  const selectors = [
    'a[href]',
    'button:not([disabled])',
    'textarea:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ];

  return Array.from(root.querySelectorAll<HTMLElement>(selectors.join(','))).filter(
    (element) => !element.hasAttribute('disabled') && !element.getAttribute('aria-hidden')
  );
}

export interface ModalProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'title'> {
  /** Controlled open state. */
  isOpen?: boolean;
  /** Legacy alias for `isOpen` (MUI-style). */
  open?: boolean;
  onClose: () => void;
  title?: React.ReactNode;
  description?: React.ReactNode;
  size?: ModalSize;
  closeOnOverlayClick?: boolean;
  closeOnEsc?: boolean;
  portalContainer?: HTMLElement | null;
  initialFocusRef?: React.RefObject<HTMLElement>;
}

/**
 * Accessible modal dialog with focus trapping and portal rendering.
 */
export const Modal = React.forwardRef<HTMLDivElement, ModalProps>((props, ref) => {
  const {
    isOpen: isOpenProp,
    open,
    onClose,
    title,
    description,
    size = 'md',
    closeOnOverlayClick = true,
    closeOnEsc = true,
    portalContainer = typeof document !== 'undefined' ? document.body : null,
    initialFocusRef,
    className,
    children,
    id,
    ...rest
  } = props;

  const isOpen = isOpenProp ?? open ?? false;

  const overlayRef = React.useRef<HTMLDivElement | null>(null);
  const contentRef = React.useRef<HTMLDivElement | null>(null);
  const mergedRef = useMergeRefs(ref, contentRef);

  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';
  const surface = isDark ? darkColors : lightColors;
  const shadow = isDark ? darkShadows[12] : lightShadows[12];

  React.useEffect(() => {
    if (!isOpen || typeof document === 'undefined') {
      return;
    }

    const previousActive = document.activeElement as HTMLElement | null;
    const content = contentRef.current;

    // Lock scroll
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    const focusFirstElement = () => {
      if (!content) {
        return;
      }

      let focusTarget: HTMLElement | null = initialFocusRef?.current ?? null;
      if (!focusTarget) {
        const focusable = getFocusableElements(content);
        focusTarget = focusable[0] ?? content;
      }

      focusTarget.focus({ preventScroll: true });
    };

    focusFirstElement();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && closeOnEsc) {
        event.preventDefault();
        onClose();
      } else if (event.key === 'Tab' && content) {
        const focusable = getFocusableElements(content);
        if (focusable.length === 0) {
          event.preventDefault();
          content.focus();
          return;
        }

        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        const active = document.activeElement as HTMLElement | null;

        if (event.shiftKey) {
          if (active === first || !active) {
            event.preventDefault();
            last.focus();
          }
        } else {
          if (active === last) {
            event.preventDefault();
            first.focus();
          }
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
      previousActive?.focus();
    };
  }, [isOpen, onClose, closeOnEsc, initialFocusRef]);

  const handleOverlayMouseDown = React.useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      if (!closeOnOverlayClick) {
        return;
      }

      if (event.target === overlayRef.current) {
        onClose();
      }
    },
    [closeOnOverlayClick, onClose]
  );

  if (!isOpen || !portalContainer) {
    return null;
  }

  const titleId = title ? `${id ?? 'gh-modal'}-title` : undefined;
  const descriptionId = description ? `${id ?? 'gh-modal'}-description` : undefined;

  const content = (
    <div
      ref={overlayRef}
      className="gh-modal__overlay"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
        backdropFilter: 'blur(2px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
        zIndex: zIndex.modal,
      }}
      onMouseDown={handleOverlayMouseDown}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
        ref={mergedRef}
        className={cn('gh-modal', className)}
        style={{
          maxWidth: sizeMap[size],
          width: '100%',
          maxHeight: 'min(90vh, 960px)',
          overflow: 'auto',
          borderRadius: `${componentRadius.modal}px`,
          backgroundColor: surface.background.elevated,
          boxShadow: shadow,
          color: surface.text.primary,
          display: 'flex',
          flexDirection: 'column',
        }}
        {...rest}
      >
        <div
          className="gh-modal__header"
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            padding: '24px',
            gap: '16px',
          }}
        >
          <div style={{ flex: '1 1 auto' }}>
            {title ? (
              <h2
                id={titleId}
                style={{
                  margin: 0,
                  fontSize: fontSize.xl,
                  fontWeight: fontWeight.semibold,
                }}
              >
                {title}
              </h2>
            ) : null}
            {description ? (
              <p
                id={descriptionId}
                style={{
                  marginTop: '8px',
                  marginBottom: 0,
                  fontSize: fontSize.base,
                  color: surface.text.secondary,
                }}
              >
                {description}
              </p>
            ) : null}
          </div>

          <IconButton
            icon={<span aria-hidden="true">&times;</span>}
            label="Close dialog"
            onClick={onClose}
            tone="neutral"
            variant="ghost"
            size="sm"
          />
        </div>

        <div
          className="gh-modal__body"
          style={{
            padding: '0 24px 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
          }}
        >
          {children}
        </div>
      </div>
    </div>
  );

  return ReactDOM.createPortal(content, portalContainer);
});

Modal.displayName = 'Modal';
