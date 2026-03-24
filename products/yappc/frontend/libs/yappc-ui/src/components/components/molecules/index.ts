/**
 * Molecules — Simple Component Combinations
 *
 * Molecules are combinations of atoms that form simple, functional units.
 * They represent common UI patterns and are highly reusable.
 *
 * @packageDocumentation
 */

// Re-export molecules from parent directory for backward compatibility
// This allows gradual migration while maintaining existing imports

export { TextField } from '../TextField';
export type { TextFieldProps } from '../TextField';

export { Select } from '../Select';
export type { SelectProps } from '../Select';

export * from '../Search';

export * from '../Alert';

export * from '../Toast';

export * from '../Status';

export { Accordion } from '../Accordion';
export type { AccordionProps } from '../Accordion';

export { Breadcrumb } from '../Breadcrumb';
export type { BreadcrumbProps } from '../Breadcrumb';

export { Pagination } from '../Pagination';
export type { PaginationProps } from '../Pagination';

/**
 * Molecule components combine atoms to create functional UI patterns.
 * They handle common interactions and form elements.
 *
 * @example Form Elements
 * ```tsx
 * import { TextField, Select, Alert } from '@ghatana/yappc-shared-ui-core/molecules';
 *
 * function ContactForm() {
 *   const [errors, setErrors] = useState([]);
 *
 *   return (
 *     <form>
 *       {errors.length > 0 && (
 *         <Alert severity="error">Please fix the errors below</Alert>
 *       )}
 *
 *       <TextField
 *         label="Name"
 *         placeholder="Enter your name"
 *         required
 *       />
 *
 *       <Select
 *         label="Country"
 *         options={countries}
 *       />
 *     </form>
 *   );
 * }
 * ```
 *
 * @example Navigation
 * ```tsx
 * import { Breadcrumb, Pagination } from '@ghatana/yappc-shared-ui-core/molecules';
 *
 * function ProductList({ products, currentPage, totalPages }) {
 *   return (
 *     <div>
 *       <Breadcrumb
 *         items={[
 *           { label: 'Home', href: '/' },
 *           { label: 'Products', href: '/products' },
 *           { label: 'Laptops' }
 *         ]}
 *       />
 *
 *       Product list content here
 *
 *       <Pagination
 *         page={currentPage}
 *         count={totalPages}
 *         onChange={handlePageChange}
 *       />
 *     </div>
 *   );
 * }
 * ```
 */
