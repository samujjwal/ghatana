/**
 * SidePanel Component Types
 *
 * @module DevSecOps/SidePanel/types
 */

/**
 * Props for the SidePanel component
 *
 * @property open - Whether the panel is open
 * @property onClose - Callback when panel should close
 * @property title - Panel title
 * @property children - Panel content
 * @property width - Panel width in pixels
 *
 * @example
 * ```typescript
 * <SidePanel
 *   open={isPanelOpen}
 *   onClose={() => setIsPanelOpen(false)}
 *   title="Item Details"
 *   width={480}
 * >
 *   <ItemDetails item={selectedItem} />
 * </SidePanel>
 * ```
 */
export interface SidePanelProps {
  /** Panel open state */
  open: boolean;

  /** Close callback */
  onClose: () => void;

  /** Panel title */
  title: string;

  /** Panel content */
  children: React.ReactNode;

  /** Panel width in pixels */
  width?: number;
}
