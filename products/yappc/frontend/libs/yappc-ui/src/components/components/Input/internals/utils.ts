import type { MaskType, FormatType } from './types';

/**
 * Utility class for Input component formatting and masking operations.
 */
export class InputUtils {
  /**
   * Get the mask pattern for a given mask type.
   *
   * @param mask - The mask type or undefined
   * @param customPattern - Custom pattern when mask is 'custom'
   * @returns The mask pattern string
   *
   * @example
   * ```tsx
   * InputUtils.getMaskPattern('phone') // => '(###) ###-####'
   * InputUtils.getMaskPattern('custom', '##/##/####') // => '##/##/####'
   * ```
   */
  static getMaskPattern(
    mask: MaskType | undefined,
    customPattern?: string
  ): string {
    if (!mask || mask === 'custom') return customPattern || '';

    switch (mask) {
      case 'phone':
        return '(###) ###-####';
      case 'date':
        return '##/##/####';
      case 'time':
        return '##:##';
      case 'creditCard':
        return '#### #### #### ####';
      case 'zipCode':
        return '#####-####';
      case 'currency':
        return '$#,###.##';
      default:
        return '';
    }
  }

  /**
   * Apply mask to an input value.
   *
   * Pattern characters:
   * - `#`: Digit (0-9)
   * - `A`: Letter (a-z, A-Z)
   * - `?`: Alphanumeric (a-z, A-Z, 0-9)
   * - Any other character: Literal character in the pattern
   *
   * @param value - The raw input value
   * @param mask - The mask type or undefined
   * @param maskPattern - Custom mask pattern
   * @returns The masked value
   *
   * @example
   * ```tsx
   * InputUtils.applyMask('1234567890', 'phone') // => '(123) 456-7890'
   * InputUtils.applyMask('12312024', 'date') // => '12/31/2024'
   * ```
   */
  static applyMask(
    value: string,
    mask: MaskType | undefined,
    maskPattern?: string
  ): string {
    if (!mask) return value;

    const pattern = InputUtils.getMaskPattern(mask, maskPattern);
    if (!pattern) return value;

    let result = '';
    let valueIndex = 0;

    for (let i = 0; i < pattern.length && valueIndex < value.length; i++) {
      // eslint-disable-next-line security/detect-object-injection
      const patternChar = pattern[i];
      // eslint-disable-next-line security/detect-object-injection
      const valueChar = value[valueIndex];

      if (patternChar === '#') {
        // Digit
        if (/\d/.test(valueChar)) {
          result += valueChar;
          valueIndex++;
        } else {
          // Skip non-digit characters in the input
          valueIndex++;
          i--; // Try this pattern character again
        }
      } else if (patternChar === 'A') {
        // Letter
        if (/[a-zA-Z]/.test(valueChar)) {
          result += valueChar;
          valueIndex++;
        } else {
          // Skip non-letter characters in the input
          valueIndex++;
          i--; // Try this pattern character again
        }
      } else if (patternChar === '?') {
        // Alphanumeric
        if (/[a-zA-Z0-9]/.test(valueChar)) {
          result += valueChar;
          valueIndex++;
        } else {
          // Skip non-alphanumeric characters in the input
          valueIndex++;
          i--; // Try this pattern character again
        }
      } else {
        // Special character in the pattern (e.g., '/', '-', etc.)
        result += patternChar;

        // If the value character matches the special character, skip it
        if (valueChar === patternChar) {
          valueIndex++;
        }
      }
    }

    return result;
  }

  /**
   * Extract raw value from a masked input.
   *
   * Removes all non-alphanumeric special characters that are part of the mask pattern.
   *
   * @param maskedValue - The masked value
   * @param mask - The mask type
   * @param maskPattern - Custom mask pattern
   * @returns The raw unmasked value
   *
   * @example
   * ```tsx
   * InputUtils.getRawValue('(123) 456-7890', 'phone') // => '1234567890'
   * ```
   */
  static getRawValue(
    maskedValue: string,
    mask: MaskType | undefined,
    maskPattern?: string
  ): string {
    if (!mask) return maskedValue;

    const pattern = InputUtils.getMaskPattern(mask, maskPattern);
    if (!pattern) return maskedValue;

    // Remove all non-alphanumeric characters if they're part of the pattern
    let rawValue = maskedValue;
    for (const char of pattern) {
      if (char !== '#' && char !== 'A' && char !== '?') {
        // eslint-disable-next-line security/detect-non-literal-regexp
        rawValue = rawValue.replace(new RegExp(`\\${char}`, 'g'), '');
      }
    }

    return rawValue;
  }

  /**
   * Format input value according to the specified format type.
   *
   * @param value - The input value to format
   * @param format - The format type
   * @param customFormatter - Custom formatter function when format is 'custom'
   * @returns The formatted value
   *
   * @example
   * ```tsx
   * InputUtils.applyFormat('hello', 'uppercase') // => 'HELLO'
   * InputUtils.applyFormat('hello world', 'capitalize') // => 'Hello World'
   * InputUtils.applyFormat('1234.5', 'percent') // => '1234.5%'
   * ```
   */
  static applyFormat(
    value: string,
    format: FormatType | undefined,
    customFormatter?: (v: string) => string
  ): string {
    if (!format || !value) return value;

    if (format === 'custom' && customFormatter) {
      return customFormatter(value);
    }

    switch (format) {
      case 'uppercase':
        return value.toUpperCase();
      case 'lowercase':
        return value.toLowerCase();
      case 'capitalize':
        return value
          .split(' ')
          .map(
            (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
          )
          .join(' ');
      case 'number': {
        // Format number with commas
        // eslint-disable-next-line security/detect-unsafe-regex
        return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
      }
      case 'percent': {
        // Format as percentage
        const num = parseFloat(value);
        return isNaN(num) ? value : `${num}%`;
      }
      case 'email':
        // Format as email (lowercase)
        return value.toLowerCase();
      case 'trim':
        // Trim whitespace
        return value.trim();
      default:
        return value;
    }
  }

  /**
   * Get theme colors based on theme type and error state.
   *
   * @param theme - 'light' | 'dark'
   * @returns Object containing color definitions
   */
  static getThemeColors(theme: 'light' | 'dark'): Record<string, string> {
    const colorMap: Record<string, Record<string, string>> = {
      light: {
        background: 'white',
        backgroundDisabled: '#f5f5f5',
        text: '#333333',
        border: '#e0e0e0',
        borderFocus: '#2196f3',
        error: '#f44336',
        icon: '#757575',
        placeholder: '#9e9e9e',
        helperText: '#757575',
        counter: '#757575',
      },
      dark: {
        background: '#333333',
        backgroundDisabled: '#424242',
        text: '#ffffff',
        border: '#555555',
        borderFocus: '#64b5f6',
        error: '#e57373',
        icon: '#bbbbbb',
        placeholder: '#aaaaaa',
        helperText: '#bbbbbb',
        counter: '#bbbbbb',
      },
    };

    // eslint-disable-next-line security/detect-object-injection
    return colorMap[theme];
  }

  /**
   * Get error color based on severity level and theme.
   *
   * @param severity - 'error' | 'warning' | 'info'
   * @param theme - 'light' | 'dark'
   * @returns The error color hex code
   */
  static getErrorColor(
    severity: 'error' | 'warning' | 'info',
    theme: 'light' | 'dark'
  ): string {
    const colorMap: Record<string, Record<string, string>> = {
      error: {
        light: '#f44336',
        dark: '#e57373',
      },
      warning: {
        light: '#ff9800',
        dark: '#ffb74d',
      },
      info: {
        light: '#2196f3',
        dark: '#64b5f6',
      },
    };

    // eslint-disable-next-line security/detect-object-injection
    const severityMap = colorMap[severity];
    if (!severityMap) return '#f44336';

    // eslint-disable-next-line security/detect-object-injection
    return severityMap[theme] || '#f44336';
  }
}
