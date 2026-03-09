import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merges Tailwind CSS class names, resolving conflicts via twMerge
 * and handling conditional classes via clsx.
 *
 * @param inputs - Class values to merge (strings, arrays, objects, etc.)
 * @returns The merged class name string
 */
export function cn(...inputs: ClassValue[]): string {
    return twMerge(clsx(inputs));
}
