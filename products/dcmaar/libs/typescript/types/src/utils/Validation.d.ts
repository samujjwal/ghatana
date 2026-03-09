/**
 * Validation utility type definition
 * Provides validation utilities for common data types
 */
export interface ValidationOptions {
    allowNull?: boolean;
    allowUndefined?: boolean;
    trim?: boolean;
    lowercase?: boolean;
    uppercase?: boolean;
}
export type StringValidator = (value: string, options?: ValidationOptions) => boolean;
export type NumberValidator = (value: number, options?: ValidationOptions) => boolean;
export type DateValidator = (value: Date, options?: ValidationOptions) => boolean;
export declare const Validation: {
    isEmail(value: string): boolean;
    isUrl(value: string): boolean;
    isPhoneNumber(value: string): boolean;
    isStrongPassword(value: string, minLength?: number): boolean;
    isEmpty(value: unknown): boolean;
    isNotEmpty(value: unknown): boolean;
    isBetween(value: number, min: number, max: number): boolean;
    isValidDate(value: Date): boolean;
};
//# sourceMappingURL=Validation.d.ts.map