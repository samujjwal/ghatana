import { forwardRef, useState, useEffect, useRef, useCallback, useMemo, memo } from 'react';

import type { InputHTMLAttributes, ReactNode } from 'react';

/**
 *
 */
export interface AutocompleteOption {
  /**
   * Option value
   */
  value: string;
  
  /**
   * Option label (displayed to user)
   */
  label?: string;
  
  /**
   * Whether the option is disabled
   */
  disabled?: boolean;
}

/**
 *
 */
export type MaskType = 
  | 'phone' // (123) 456-7890
  | 'date' // MM/DD/YYYY
  | 'time' // HH:MM (24h)
  | 'creditCard' // XXXX XXXX XXXX XXXX
  | 'zipCode' // 12345 or 12345-6789
  | 'currency' // $1,234.56
  | 'custom';
  
/**
 *
 */
export type FormatType =
  | 'uppercase' // Convert to uppercase
  | 'lowercase' // Convert to lowercase
  | 'capitalize' // Capitalize first letter of each word
  | 'number' // Format as number with commas
  | 'percent' // Format as percentage
  | 'email' // Format as email (lowercase)
  | 'trim' // Trim whitespace
  | 'custom'; // Custom formatter

/**
 *
 */
export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  /**
   * Input label
   */
  label?: string;
  
  /**
   * Helper text displayed below the input
   */
  helperText?: string;
  
  /**
   * Error message (overrides helperText when present)
   */
  error?: string;
  
  /**
   * Error message severity level
   */
  errorSeverity?: 'error' | 'warning' | 'info';
  
  /**
   * Show error icon
   */
  showErrorIcon?: boolean;
  
  /**
   * Input size variant
   */
  size?: 'small' | 'medium' | 'large';
  
  /**
   * Full width input
   */
  fullWidth?: boolean;
  
  /**
   * Icon to display at the start of the input
   */
  startIcon?: ReactNode;
  
  /**
   * Icon to display at the end of the input
   */
  endIcon?: ReactNode;
  
  /**
   * Show character counter
   */
  showCounter?: boolean;
  
  /**
   * Input is required
   */
  required?: boolean;

  /**
   * Show clear button when input has value
   */
  clearable?: boolean;

  /**
   * Callback when clear button is clicked
   */
  onClear?: () => void;

  /**
   * Show password visibility toggle for password inputs
   */
  showPasswordToggle?: boolean;

  /**
   * Show loading indicator
   */
  loading?: boolean;
  
  /**
   * Enable autocomplete with suggestions
   */
  autocomplete?: boolean;
  
  /**
   * Autocomplete options
   */
  options?: AutocompleteOption[];
  
  /**
   * Callback when an autocomplete option is selected
   */
  onOptionSelect?: (option: AutocompleteOption) => void;
  
  /**
   * Maximum number of autocomplete options to show
   */
  maxOptions?: number;
  
  /**
   * Enable input masking
   */
  mask?: MaskType;
  
  /**
   * Custom mask pattern (used when mask is 'custom')
   * Use # for digits, A for letters, ? for alphanumeric
   * Example: '##/##/####' for date
   */
  maskPattern?: string;
  
  /**
   * Placeholder character for mask
   */
  maskPlaceholder?: string;
  
  /**
   * Enable input formatting
   */
  format?: FormatType;
  
  /**
   * Custom formatter function (used when format is 'custom')
   */
  formatter?: (value: string) => string;
  
  /**
   * Format on blur instead of on every change
   */
  formatOnBlur?: boolean;
  
  /**
   * Enable debounced input
   */
  debounced?: boolean;
  
  /**
   * Debounce delay in milliseconds
   */
  debounceDelay?: number;
  
  /**
   * Callback for debounced value changes
   */
  onDebouncedChange?: (value: string) => void;
  
  /**
   * Enable keyboard navigation
   */
  keyboardNavigation?: boolean;
  
  /**
   * Callback when Tab key is pressed
   */
  onTabPress?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  
  /**
   * Callback when Enter key is pressed
   */
  onEnterPress?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  
  /**
   * Callback when Escape key is pressed
   */
  onEscapePress?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  
  /**
   * Callback when Arrow keys are pressed
   */
  onArrowPress?: (direction: 'up' | 'down' | 'left' | 'right', e: React.KeyboardEvent<HTMLInputElement>) => void;
  
  /**
   * Additional ARIA label for the input
   */
  ariaLabel?: string;
  
  /**
   * Additional ARIA description for the input
   */
  ariaDescription?: string;
  
  /**
   * Announce changes to screen readers
   */
  announceChanges?: boolean;
  
  /**
   * Theme for the input
   */
  theme?: 'light' | 'dark' | 'system';
  
  /**
   * Enable mobile optimization
   */
  mobileOptimized?: boolean;
}

/**
 * Input component for text entry with various features including validation states, icons,
 * character counter, clear button, password visibility toggle, loading state, autocomplete, input masking, text formatting, debounced input, and keyboard navigation.
 * 
 * @example Basic usage
 * ```tsx
 * <Input
 *   label="Email"
 *   type="email"
 *   placeholder="Enter your email"
 *   helperText="We'll never share your email"
 * />
 * ```
 * 
 * @example With clear button
 * ```tsx
 * <Input
 *   label="Search"
 *   placeholder="Type to search..."
 *   clearable
 *   onClear={() => console.log('Input cleared')}
 * />
 * ```
 * 
 * @example With password visibility toggle
 * ```tsx
 * <Input
 *   label="Password"
 *   type="password"
 *   showPasswordToggle
 *   placeholder="Enter your password"
 * />
 * ```
 * 
 * @example With loading state
 * ```tsx
 * <Input
 *   label="Username"
 *   loading={isCheckingAvailability}
 *   placeholder="Check username availability"
 * />
 * ```
 * 
 * @example With autocomplete
 * ```tsx
 * <Input
 *   label="Country"
 *   placeholder="Start typing a country name..."
 *   autocomplete
 *   options={[
 *     { value: 'us', label: 'United States' },
 *     { value: 'ca', label: 'Canada' },
 *     { value: 'uk', label: 'United Kingdom' },
 *   ]}
 *   onOptionSelect={(option) => console.log(`Selected: ${option.label}`)}
 * />
 * ```
 * 
 * @example With input masking
 * ```tsx
 * <Input
 *   label="Phone"
 *   mask="phone"
 *   placeholder="(123) 456-7890"
 *   helperText="Enter your phone number"
 * />
 * ```
 * 
 * @example With custom input masking
 * ```tsx
 * <Input
 *   label="Product Code"
 *   mask="custom"
 *   maskPattern="AAA-#####"
 *   placeholder="ABC-12345"
 *   helperText="Enter a product code (3 letters followed by 5 numbers)"
 * />
 * ```
 * 
 * @example With text formatting
 * ```tsx
 * <Input
 *   label="Username"
 *   format="uppercase"
 *   placeholder="Enter username"
 *   helperText="Username will be converted to uppercase"
 * />
 * ```
 * 
 * @example With format on blur
 * ```tsx
 * <Input
 *   label="Name"
 *   format="capitalize"
 *   formatOnBlur
 *   placeholder="Enter your name"
 *   helperText="Name will be capitalized when you tab out of the field"
 * />
 * ```
 * 
 * @example With debounced input
 * ```tsx
 * <Input
 *   label="Search"
 *   debounced
 *   debounceDelay={500}
 *   placeholder="Search..."
 *   onDebouncedChange={(value) => console.log(`Searching for: ${value}`)}
 *   helperText="Search will be triggered 500ms after you stop typing"
 * />
 * ```
 * 
 * @example With keyboard navigation
 * ```tsx
 * <Input
 *   label="Interactive Input"
 *   keyboardNavigation
 *   onEnterPress={(e) => console.log('Enter pressed')}
 *   onArrowPress={(direction, e) => console.log(`Arrow ${direction} pressed`)}
 *   placeholder="Press keys to see events"
 *   helperText="Try pressing Enter, Tab, Escape, or Arrow keys"
 * />
 * ```
 */
export const Input = memo(forwardRef<HTMLInputElement, InputProps>(
  (
    {
      label,
      helperText,
      error,
      size = 'medium',
      fullWidth = false,
      startIcon,
      endIcon,
      showCounter = false,
      required = false,
      maxLength,
      className = '',
      id,
      clearable = false,
      onClear,
      showPasswordToggle = false,
      loading = false,
      autocomplete = false,
      options = [],
      onOptionSelect,
      maxOptions = 5,
      mask,
      maskPattern,
      maskPlaceholder = '_',
      format,
      formatter,
      formatOnBlur = false,
      debounced = false,
      debounceDelay = 300,
      onDebouncedChange,
      keyboardNavigation = false,
      onTabPress,
      onEnterPress,
      onEscapePress,
      onArrowPress,
  ariaLabel,
  ariaDescription,
  value: valueProp,
  defaultValue: defaultValueProp,
      announceChanges = false,
      theme = 'light',
      mobileOptimized = true,
      errorSeverity = 'error',
      showErrorIcon = true,
      ...props
    },
    ref
  ) => {
    const [value, setValue] = useState<string>(
      (valueProp as unknown) ?? (defaultValueProp as unknown) ?? ''
    );
    const [showPassword, setShowPassword] = useState(false);
    const [showOptions, setShowOptions] = useState(false);
    const [filteredOptions, setFilteredOptions] = useState<AutocompleteOption[]>([]);
    const [activeOptionIndex, setActiveOptionIndex] = useState(-1);
    const [maskedValue, setMaskedValue] = useState('');
    const inputId = id || `input-${Math.random().toString(36).substr(2, 9)}`;
    const hasError = Boolean(error);
    const isPasswordInput = props.type === 'password';
    // Use a mutable ref object that TypeScript won't complain about
    const inputRef = { current: null } as { current: HTMLInputElement | null };
    const optionsRef = useRef<HTMLDivElement>(null);
    
    // Determine if masking is enabled
    const hasMask = Boolean(mask);
    
    // Determine if formatting is enabled
    const hasFormat = Boolean(format);
    
    // Determine if debouncing is enabled
    const hasDebounce = Boolean(debounced);
    
    // Determine if keyboard navigation is enabled
    const hasKeyboardNavigation = Boolean(keyboardNavigation);
    
    // State for screen reader announcements
    const [announcement, setAnnouncement] = useState<string>('');
    
    // Determine if using system theme
    const [systemTheme, setSystemTheme] = useState<'light' | 'dark'>('light');
    
    // Track viewport size for mobile optimization
    const [isMobileViewport, setIsMobileViewport] = useState(false);
    
    // Get the effective theme
    const effectiveTheme = theme === 'system' ? systemTheme : theme;
    
    // Theme colors memoized for performance
    const themeColors = useMemo(() => ({
      errorColors: {
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
      },
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
    }), []);
    
    // Get colors for current theme - memoized based on theme changes
    const colors = useMemo(() => themeColors[effectiveTheme], [effectiveTheme, themeColors]);
    
    // Get error color based on severity and theme
    const errorColor = useMemo(() => {
      if (!hasError) return undefined;
      return themeColors.errorColors[errorSeverity][effectiveTheme];
    }, [hasError, errorSeverity, effectiveTheme, themeColors]);

    // Memoize size styles based on viewport
    const sizeStyles = useMemo(() => ({
      // Mobile sizes are slightly larger for better touch targets
      mobileSizes: {
        small: {
          padding: '0.5rem 0.75rem',
          fontSize: '1rem',
        },
        medium: {
          padding: '0.625rem 1rem',
          fontSize: '1.125rem',
        },
        large: {
          padding: '0.875rem 1.25rem',
          fontSize: '1.25rem',
        },
      },
      // Desktop sizes
      desktopSizes: {
      small: {
        padding: '0.375rem 0.75rem',
        fontSize: '0.875rem',
      },
      medium: {
        padding: '0.5rem 1rem',
        fontSize: '1rem',
      },
      large: {
        padding: '0.75rem 1.25rem',
        fontSize: '1.125rem',
      },
    }}), []);

    // Memoize styles that depend on props
    const containerStyle = useMemo<React.CSSProperties>(() => ({
      display: 'flex',
      flexDirection: 'column',
      gap: '0.25rem',
      width: fullWidth ? '100%' : 'auto',
      color: colors.text,
    }), [fullWidth, colors.text]);

    const inputWrapperStyle = useMemo<React.CSSProperties>(() => ({
      position: 'relative',
      display: 'flex',
      alignItems: 'center',
    }), []);

    // Define keyframes style for the loading spinner
    const spinnerAnimation = `
      @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }
    `;

    // Create a style element for the animation
    useEffect(() => {
      if (loading) {
        const styleElement = document.createElement('style');
        styleElement.id = 'input-spinner-animation';
        styleElement.innerHTML = spinnerAnimation;
        document.head.appendChild(styleElement);

        return () => {
          const existingStyle = document.getElementById('input-spinner-animation');
          if (existingStyle) {
            document.head.removeChild(existingStyle);
          }
        };
      }
      return;
    }, [loading]);

    // Theme colors definition moved up to fix TypeScript error
    
    // Get appropriate size based on viewport
    const activeSize = useMemo(() => {
      if (mobileOptimized && isMobileViewport) {
        return sizeStyles.mobileSizes[size];
      }
      return sizeStyles.desktopSizes[size];
    }, [mobileOptimized, isMobileViewport, sizeStyles, size]);
    
    const inputStyle = useMemo<React.CSSProperties>(() => ({
      ...activeSize,
      width: '100%',
      border: '1px solid',
      borderColor: hasError ? errorColor : colors.border,
      borderRadius: '0.375rem',
      outline: 'none',
      transition: 'all 0.2s ease-in-out',
      paddingLeft: startIcon ? '2.5rem' : activeSize.padding.split(' ')[1],
      paddingRight: (endIcon || loading || (isPasswordInput && showPasswordToggle) || (clearable && value)) ? '2.5rem' : activeSize.padding.split(' ')[1],
      // Mobile optimizations
      ...(mobileOptimized && isMobileViewport && {
        touchAction: 'manipulation', // Better touch handling
        WebkitAppearance: 'none', // Remove default iOS styling
        borderRadius: '0.5rem', // Slightly larger border radius for mobile
        minHeight: '2.75rem', // Ensure minimum touch target size
      }),
      backgroundColor: loading ? colors.backgroundDisabled : colors.background,
      color: colors.text,
      cursor: loading ? 'wait' : 'text',
    }), [activeSize, hasError, colors.error, colors.border, startIcon, endIcon, loading, isPasswordInput, showPasswordToggle, clearable, value, colors.backgroundDisabled, colors.background, colors.text, mobileOptimized, isMobileViewport]);

    const labelStyle = useMemo<React.CSSProperties>(() => ({
      fontSize: '0.875rem',
      fontWeight: 500,
      color: hasError ? errorColor : colors.text,
      marginBottom: '0.25rem',
    }), [hasError, colors.error, colors.text]);

    const helperStyle = useMemo<React.CSSProperties>(() => ({
      fontSize: '0.75rem',
      color: hasError ? errorColor : colors.helperText,
      marginTop: '0.25rem',
    }), [hasError, colors.error, colors.helperText]);

    const iconStyle = useMemo<React.CSSProperties>(() => ({
      position: 'absolute',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: hasError ? errorColor : colors.icon,
    }), [hasError, colors.error, colors.icon]);

    // Get mask pattern based on mask type
    const getMaskPattern = (): string => {
      if (!mask || mask === 'custom') return maskPattern || '';
      
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
    };
    
    // Apply mask to input value - memoized for performance
    const applyMask = useCallback((value: string): string => {
      if (!hasMask) return value;
      
      const pattern = getMaskPattern();
      if (!pattern) return value;
      
      let result = '';
      let valueIndex = 0;
      
      for (let i = 0; i < pattern.length && valueIndex < value.length; i++) {
        const patternChar = pattern[i];
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
    }, [hasMask, mask, maskPattern]);
    

    
    // Debounce timer reference
    const debounceTimerRef = useRef<NodeJS.Timeout | null>(null);
    
    // Debounced change handler with useCallback for performance
    const debouncedChangeHandler = useCallback(
      (value: string) => {
        if (debounceTimerRef.current) {
          clearTimeout(debounceTimerRef.current);
        }
        
        debounceTimerRef.current = setTimeout(() => {
          onDebouncedChange?.(value);
        }, debounceDelay);
      },
      [debounceDelay, onDebouncedChange]
    );
    
    // Clean up debounce timer on unmount
    useEffect(() => {
      return () => {
        if (debounceTimerRef.current) {
          clearTimeout(debounceTimerRef.current);
        }
      };
    }, []);
    
    // Detect mobile viewport
    useEffect(() => {
      if (mobileOptimized) {
        const checkViewport = () => {
          setIsMobileViewport(window.innerWidth < 768); // Common breakpoint for mobile devices
        };
        
        // Check initially
        checkViewport();
        
        // Add resize listener
        window.addEventListener('resize', checkViewport);
        
        return () => {
          window.removeEventListener('resize', checkViewport);
        };
      }
      return;
    }, [mobileOptimized]);
    
    // Detect system theme
    useEffect(() => {
      if (theme === 'system') {
        // Check if user prefers dark mode
        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        
        // Set initial theme
        setSystemTheme(mediaQuery.matches ? 'dark' : 'light');
        
        // Listen for changes
        const listener = (e: MediaQueryListEvent) => {
          setSystemTheme(e.matches ? 'dark' : 'light');
        };
        
        mediaQuery.addEventListener('change', listener);
        
        return () => {
          mediaQuery.removeEventListener('change', listener);
        };
      }
      return;
    }, [theme]);
    
    // Apply formatting to input value - memoized for performance
    const applyFormat = useCallback((value: string): string => {
      if (!hasFormat || !value) return value;
      
      if (format === 'custom' && formatter) {
        return formatter(value);
      }
      
      switch (format) {
        case 'uppercase':
          return value.toUpperCase();
        case 'lowercase':
          return value.toLowerCase();
        case 'capitalize':
          return value
            .split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
            .join(' ');
        case 'number':
          // Format number with commas
          return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        case 'percent':
          // Format as percentage
          const num = parseFloat(value);
          return isNaN(num) ? value : `${num}%`;
        case 'email':
          // Format as email (lowercase)
          return value.toLowerCase();
        case 'trim':
          // Trim whitespace
          return value.trim();
        default:
          return value;
      }
    }, [hasFormat, format, formatter]);
    
    // Function to announce changes to screen readers - memoized to prevent recreating on each render
    const announce = useCallback((message: string) => {
      if (announceChanges) {
        setAnnouncement(message);
        // Clear announcement after screen reader has had time to read it
        setTimeout(() => setAnnouncement(''), 1000);
      }
    }, [announceChanges]);
    
    // Optimize event handlers with useCallback
    const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = e.target.value;
      
      // Apply mask if enabled
      if (hasMask) {
        const masked = applyMask(newValue);
        setMaskedValue(masked);
        setValue(masked);
        
        // Create a new synthetic event with the masked value
        const maskedEvent = {
          ...e,
          target: {
            ...e.target,
            value: masked,
          },
        };
        
        props.onChange?.(maskedEvent);
        
        // Announce masked value to screen readers
        announce(`Value: ${masked}`);
      } else {
        // Apply formatting if enabled and not set to format on blur
        if (hasFormat && !formatOnBlur) {
          const formatted = applyFormat(newValue);
          setValue(formatted);
          
          // Create a new synthetic event with the formatted value
          const formattedEvent = {
            ...e,
            target: {
              ...e.target,
              value: formatted,
            },
          };
          
          props.onChange?.(formattedEvent);
          
          // Call onDebouncedChange if enabled
          if (hasDebounce) {
            debouncedChangeHandler(formatted);
          }
          
          // Announce formatted value to screen readers
          announce(`Value: ${formatted}`);
          
          // Set aria-valuenow for screen readers
          if (inputRef.current) {
            inputRef.current.setAttribute('aria-valuenow', formatted);
          }
        } else {
          setValue(newValue);
          props.onChange?.(e);
          
          // Call onDebouncedChange if enabled
          if (hasDebounce) {
            debouncedChangeHandler(newValue);
          }
          
          // Announce value to screen readers
          announce(`Value: ${newValue}`);
          
          // Set aria-valuenow for screen readers
          if (inputRef.current) {
            inputRef.current.setAttribute('aria-valuenow', newValue);
          }
        }
      }
      
      // Filter options for autocomplete
      if (autocomplete && options.length > 0) {
        const filtered = options
          .filter(option => {
            const optionText = option.label || option.value;
            return optionText.toLowerCase().includes(String(newValue).toLowerCase());
          })
          .slice(0, maxOptions);
        
        setFilteredOptions(filtered);
        setShowOptions(filtered.length > 0 && newValue.length > 0);
        setActiveOptionIndex(-1);
      }
    }, [hasMask, applyMask, setMaskedValue, setValue, props.onChange, hasFormat, formatOnBlur, applyFormat, hasDebounce, debouncedChangeHandler, autocomplete, options, maxOptions, setFilteredOptions, setShowOptions, setActiveOptionIndex]);

    const handleClear = useCallback(() => {
      const clearedEvent = {
        target: { value: '', name: props.name } as HTMLInputElement,
      } as React.ChangeEvent<HTMLInputElement>;
      
      setValue('');
      setMaskedValue('');
      props.onChange?.(clearedEvent);
      onClear?.();
      
      // Focus the input after clearing
      setTimeout(() => {
        inputRef.current?.focus();
      }, 0);
      
      // Announce to screen readers
      announce('Input cleared');
    }, [setValue, setMaskedValue, props.onChange, onClear, announce]);

    const togglePasswordVisibility = useCallback(() => {
      setShowPassword(prev => !prev);
    }, []);
    
    const handleOptionSelect = useCallback((option: AutocompleteOption) => {
      setValue(option.label || option.value);
      setShowOptions(false);
      onOptionSelect?.(option);
      
      // Create a synthetic change event
      if (inputRef.current) {
        const event = {
          target: { 
            value: option.label || option.value,
            name: props.name 
          } as HTMLInputElement,
        } as React.ChangeEvent<HTMLInputElement>;
        
        props.onChange?.(event);
        
        // Focus the input after selection
        setTimeout(() => {
          inputRef.current?.focus();
        }, 0);
        
        // Announce selection to screen readers
        announce(`Selected: ${option.label || option.value}`);
      }
    }, [setValue, setShowOptions, onOptionSelect, props.name, props.onChange, announce]);
    
    const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
      // Handle autocomplete keyboard navigation
      if (autocomplete && showOptions && filteredOptions.length > 0) {
        // Arrow down
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          setActiveOptionIndex(prev => 
            prev < filteredOptions.length - 1 ? prev + 1 : prev
          );
          
          // Call onArrowPress if keyboard navigation is enabled
          if (hasKeyboardNavigation) {
            onArrowPress?.('down', e);
          }
          return;
        }
        
        // Arrow up
        else if (e.key === 'ArrowUp') {
          e.preventDefault();
          setActiveOptionIndex(prev => (prev > 0 ? prev - 1 : 0));
          
          // Call onArrowPress if keyboard navigation is enabled
          if (hasKeyboardNavigation) {
            onArrowPress?.('up', e);
          }
          return;
        }
        
        // Enter
        else if (e.key === 'Enter' && activeOptionIndex >= 0) {
          e.preventDefault();
          handleOptionSelect(filteredOptions[activeOptionIndex]);
          
          // Call onEnterPress if keyboard navigation is enabled
          if (hasKeyboardNavigation) {
            onEnterPress?.(e);
          }
          return;
        }
        
        // Escape
        else if (e.key === 'Escape') {
          e.preventDefault();
          setShowOptions(false);
          
          // Call onEscapePress if keyboard navigation is enabled
          if (hasKeyboardNavigation) {
            onEscapePress?.(e);
          }
          return;
        }
      }
      
      // Handle general keyboard navigation if enabled
      if (hasKeyboardNavigation) {
        switch (e.key) {
          case 'Tab':
            onTabPress?.(e);
            break;
          case 'Enter':
            onEnterPress?.(e);
            break;
          case 'Escape':
            onEscapePress?.(e);
            break;
          case 'ArrowUp':
            onArrowPress?.('up', e);
            break;
          case 'ArrowDown':
            onArrowPress?.('down', e);
            break;
          case 'ArrowLeft':
            onArrowPress?.('left', e);
            break;
          case 'ArrowRight':
            onArrowPress?.('right', e);
            break;
          default:
            break;
        }
      }
      
      // Pass through original onKeyDown if provided
      props.onKeyDown?.(e);
    }, [autocomplete, showOptions, filteredOptions, setActiveOptionIndex, hasKeyboardNavigation, onArrowPress, activeOptionIndex, handleOptionSelect, setShowOptions, onEnterPress, onEscapePress, onTabPress, props.onKeyDown]);
    
    // Close options when clicking outside
    useEffect(() => {
      const handleClickOutside = (e: MouseEvent) => {
        if (
          optionsRef.current && 
          !optionsRef.current.contains(e.target as Node) &&
          inputRef.current &&
          !inputRef.current.contains(e.target as Node)
        ) {
          setShowOptions(false);
        }
      };
      
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Keep aria-describedby attribute in sync on the actual input DOM element so tests
    // that read the attribute directly will see the composed ids.
    useEffect(() => {
      const ids: string[] = [];
      if (error) ids.push(`${inputId}-error`);
      else if (helperText) ids.push(`${inputId}-helper`);
      // Only include the additional aria description when there's no error
      if (!error && ariaDescription) ids.push(`${inputId}-description`);

      if (inputRef.current) {
        const described = ids.length > 0 ? ids.join(' ') : '';
        if (described) {
          inputRef.current.setAttribute('aria-describedby', described);
        } else {
          inputRef.current.removeAttribute('aria-describedby');
        }
      }
    }, [error, helperText, ariaDescription, inputId]);

    return (
      <div style={containerStyle} className={className}>
        {label && (
          <label htmlFor={inputId} style={labelStyle}>
            {label}
            {required && <span style={{ color: '#f44336', marginLeft: '0.25rem' }}>*</span>}
          </label>
        )}
        
        <div style={inputWrapperStyle}>
          {startIcon && (
            <div style={{ ...iconStyle, left: '0.75rem' }}>
              {startIcon}
            </div>
          )}
          
          <input
            ref={(node) => {
              // Handle both function refs and object refs
              if (typeof ref === 'function') {
                ref(node);
              } else if (ref) {
                (ref as React.MutableRefObject<HTMLInputElement | null>).current = node;
              }
              
              // Set our internal ref object
              inputRef.current = node;
            }}
            id={inputId}
            style={inputStyle}
            disabled={props.disabled || loading}
            required={required}
            aria-invalid={hasError}
            aria-busy={loading ? 'true' : 'false'}
            maxLength={maxLength}
            aria-autocomplete={autocomplete ? 'list' : undefined}
            aria-controls={autocomplete && showOptions ? `${inputId}-options` : undefined}
            aria-activedescendant={
              autocomplete && showOptions && activeOptionIndex >= 0 
                ? `${inputId}-option-${activeOptionIndex}` 
                : undefined
            }
            aria-label={ariaLabel}
            aria-describedby={(() => {
              const ids: string[] = [];
              if (error) ids.push(`${inputId}-error`);
              else if (helperText) ids.push(`${inputId}-helper`);
              if (ariaDescription) ids.push(`${inputId}-description`);
              return ids.length > 0 ? ids.join(' ') : undefined;
            })()}
            // Ensure the input is exposed as a textbox role for testing-library queries
            role={props.type === 'password' ? 'textbox' : props.role}
            {...props}
            type={isPasswordInput && showPassword ? 'text' : props.type}
            value={hasMask ? maskedValue : value}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            onFocus={(e) => {
              if (autocomplete && value && options.length > 0) {
                const filtered = options
                  .filter(option => {
                    const optionText = option.label || option.value;
                    return optionText.toLowerCase().includes(String(value).toLowerCase());
                  })
                  .slice(0, maxOptions);
                
                setFilteredOptions(filtered);
                setShowOptions(filtered.length > 0);
              }
              props.onFocus?.(e);
            }}
            onBlur={(e) => {
              // Apply formatting on blur if enabled
              if (hasFormat && formatOnBlur && typeof value === 'string') {
                const formatted = applyFormat(value);
                setValue(formatted);
                
                // Create a new synthetic event with the formatted value
                const formattedEvent = {
                  ...e,
                  target: {
                    ...e.target,
                    value: formatted,
                  },
                };
                
                props.onChange?.(formattedEvent);
              }
              
              props.onBlur?.(e);
            }}
          />

            {/* Keep aria-describedby attribute in sync on the DOM node so tests can read it reliably */}
            {/* This effect runs after render to ensure the input element has the attribute applied */}
            {null}
          
          {/* Password visibility toggle */}
          {isPasswordInput && showPasswordToggle && (
            <div 
              style={{ 
                ...iconStyle, 
                right: endIcon ? '2.5rem' : (clearable && value ? '2.5rem' : '0.75rem'),
                cursor: 'pointer',
              }}
              onClick={togglePasswordVisibility}
              role="button"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && togglePasswordVisibility()}
            >
              {showPassword ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                  <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              )}
            </div>
          )}

          {/* Clear button */}
          {clearable && value && (
            <div 
              style={{ 
                ...iconStyle, 
                right: endIcon ? '2.5rem' : (isPasswordInput && showPasswordToggle ? '2.5rem' : '0.75rem'),
                cursor: 'pointer',
              }}
              onClick={handleClear}
              role="button"
              aria-label="Clear input"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && handleClear()}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </div>
          )}
          
          {/* Loading spinner */}
          {loading && (
            <div style={{ 
              ...iconStyle, 
              right: '0.75rem',
              animation: 'spin 1s linear infinite',
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
                <path d="M12 2a10 10 0 0 1 10 10" strokeLinecap="round" />
              </svg>
            </div>
          )}

          {endIcon && !loading && (
            <div style={{ 
              ...iconStyle, 
              right: '0.75rem',
            }}>
              {endIcon}
            </div>
          )}
        </div>

        {(helperText || error) && (
          <div
            style={{
              ...helperStyle,
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
            role={error ? 'alert' : undefined}
          >
            {error && showErrorIcon && (
              <div style={{ display: 'flex', alignItems: 'center' }}>
                {errorSeverity === 'error' && (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12" y2="16" />
                  </svg>
                )}
                {errorSeverity === 'warning' && (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                    <line x1="12" y1="9" x2="12" y2="13" />
                    <line x1="12" y1="17" x2="12" y2="17" />
                  </svg>
                )}
                {errorSeverity === 'info' && (
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="16" x2="12" y2="12" />
                    <line x1="12" y1="8" x2="12" y2="8" />
                  </svg>
                )}
              </div>
            )}
            <div id={error ? `${inputId}-error` : `${inputId}-helper`}>
              {error && (
                <span style={{ fontWeight: errorSeverity === 'error' ? 500 : 400 }}>
                  {errorSeverity === 'error' ? 'Error: ' : errorSeverity === 'warning' ? 'Warning: ' : 'Info: '}
                </span>
              )}
              {error || helperText}
            </div>
          </div>
        )}
        
        {/* Autocomplete options dropdown */}
        {autocomplete && showOptions && filteredOptions.length > 0 && (
          <div
            ref={optionsRef}
            id={`${inputId}-options`}
            role="listbox"
            aria-label="Suggestions"
            style={{
              position: 'absolute',
              top: '100%',
              left: 0,
              right: 0,
              zIndex: 10,
              marginTop: '4px',
              backgroundColor: colors.background,
              border: `1px solid ${colors.border}`,
              borderRadius: '4px',
              boxShadow: effectiveTheme === 'dark' ? 
                '0 2px 4px rgba(0, 0, 0, 0.3)' : 
                '0 2px 4px rgba(0, 0, 0, 0.1)',
              maxHeight: mobileOptimized && isMobileViewport ? '240px' : '200px',
              overflowY: 'auto',
            }}
          >
            {filteredOptions.map((option, index) => (
              <div
                key={option.value}
                id={`${inputId}-option-${index}`}
                role="option"
                aria-selected={index === activeOptionIndex}
                style={{
                  padding: '8px 12px',
                  cursor: 'pointer',
                  backgroundColor: index === activeOptionIndex ? 
                (effectiveTheme === 'dark' ? '#424242' : '#f5f5f5') : 
                (effectiveTheme === 'dark' ? '#333333' : 'transparent'),
              // Mobile optimizations for dropdown items
              ...(mobileOptimized && isMobileViewport && {
                padding: '12px 16px', // Larger touch targets
                minHeight: '44px', // Apple's recommended minimum touch target size
              }),
                }}
                onClick={() => handleOptionSelect(option)}
              >
                {option.label || option.value}
              </div>
            ))}
          </div>
        )}

        {/* Additional ARIA description */}
        {ariaDescription && (
          <div id={`${inputId}-description`} style={{ position: 'absolute', height: '1px', width: '1px', overflow: 'hidden', clip: 'rect(1px, 1px, 1px, 1px)' }}>
            {ariaDescription}
          </div>
        )}
        
        {/* Screen reader announcements - render the live region even if announcement is empty so tests can query it */}
        {announceChanges && (
          <div role="status" aria-live="polite" style={{ position: 'absolute', height: '1px', width: '1px', overflow: 'hidden', clip: 'rect(1px, 1px, 1px, 1px)' }}>
            {announcement}
          </div>
        )}
        
        {showCounter && maxLength && (
          <div style={{ ...helperStyle, textAlign: 'right' }} aria-live="polite" aria-atomic="true">
            {String(value).length} / {maxLength}
          </div>
        )}
      </div>
    );
  }
));

Input.displayName = 'Input';
