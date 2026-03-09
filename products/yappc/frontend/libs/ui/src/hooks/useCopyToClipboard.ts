import { useState, useCallback } from 'react';

/**
 * Value of the last successfully copied text, or null if nothing copied yet
 */
type CopiedValue = string | null;

/**
 * Function to copy text to clipboard
 * @param text - Text to copy to clipboard
 * @returns Promise resolving to true if successful, false otherwise
 */
type CopyFn = (text: string) => Promise<boolean>;

/**
 * Hook that provides copy to clipboard functionality
 * 
 * @returns A tuple of [copiedText, copy function]
 * 
 * @example
 * ```tsx
 * const [copiedText, copy] = useCopyToClipboard();
 * 
 * const handleCopy = async () => {
 *   const success = await copy('Text to copy');
 *   if (success) {
 *     toast.success('Copied to clipboard!');
 *   }
 * };
 * ```
 */
export function useCopyToClipboard(): [CopiedValue, CopyFn] {
  const [copiedText, setCopiedText] = useState<CopiedValue>(null);

  const copy: CopyFn = useCallback(async (text) => {
    if (!navigator?.clipboard) {
      console.warn('Clipboard not supported');
      return false;
    }

    try {
      await navigator.clipboard.writeText(text);
      setCopiedText(text);
      return true;
    } catch (error) {
      console.warn('Copy failed', error);
      setCopiedText(null);
      return false;
    }
  }, []);

  return [copiedText, copy];
}
