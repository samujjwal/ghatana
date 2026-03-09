/**
 * Haptic Feedback System
 * 
 * Provides haptic feedback for mobile interactions
 * 
 * @doc.type service
 * @doc.purpose Haptic feedback for enhanced user experience
 * @doc.layer product
 * @doc.pattern Service
 */

import { Platform } from 'react-native';
import * as Haptics from 'expo-haptics';

export type HapticType = 
  | 'light'      // Light impact for subtle feedback
  | 'medium'     // Medium impact for standard feedback
  | 'heavy'      // Heavy impact for important actions
  | 'success'    // Success feedback for completed actions
  | 'warning'    // Warning feedback for caution
  | 'error'      // Error feedback for failures
  | 'selection'  // Selection feedback for UI interactions
  | 'notification'; // Notification feedback for alerts

/**
 * Haptic Feedback Service
 */
export class HapticService {
  private static instance: HapticService;
  private enabled: boolean = true;

  private constructor() {}

  static getInstance(): HapticService {
    if (!HapticService.instance) {
      HapticService.instance = new HapticService();
    }
    return HapticService.instance;
  }

  /**
   * Enable or disable haptic feedback
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;
  }

  /**
   * Check if haptic feedback is enabled
   */
  isEnabled(): boolean {
    return this.enabled && Platform.OS !== 'web';
  }

  /**
   * Trigger haptic feedback
   */
  trigger(type: HapticType): void {
    if (!this.isEnabled()) {
      return;
    }

    try {
      switch (type) {
        case 'light':
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
          break;
        
        case 'medium':
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
          break;
        
        case 'heavy':
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
          break;
        
        case 'success':
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
          break;
        
        case 'warning':
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
          break;
        
        case 'error':
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
          break;
        
        case 'selection':
          Haptics.selectionAsync();
          break;
        
        case 'notification':
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
          break;
        
        default:
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      }
    } catch (error) {
      // Silently fail if haptics are not supported
      console.warn('Haptic feedback not supported:', error);
    }
  }

  /**
   * Light impact for subtle interactions
   */
  light(): void {
    this.trigger('light');
  }

  /**
   * Medium impact for standard interactions
   */
  medium(): void {
    this.trigger('medium');
  }

  /**
   * Heavy impact for important actions
   */
  heavy(): void {
    this.trigger('heavy');
  }

  /**
   * Success feedback for completed actions
   */
  success(): void {
    this.trigger('success');
  }

  /**
   * Warning feedback for caution
   */
  warning(): void {
    this.trigger('warning');
  }

  /**
   * Error feedback for failures
   */
  error(): void {
    this.trigger('error');
  }

  /**
   * Selection feedback for UI interactions
   */
  selection(): void {
    this.trigger('selection');
  }

  /**
   * Notification feedback for alerts
   */
  notification(): void {
    this.trigger('notification');
  }
}

/**
 * Global haptic service instance
 */
export const haptics = HapticService.getInstance();

/**
 * Hook for using haptic feedback
 */
export const useHaptics = () => {
  return {
    trigger: haptics.trigger.bind(haptics),
    light: haptics.light.bind(haptics),
    medium: haptics.medium.bind(haptics),
    heavy: haptics.heavy.bind(haptics),
    success: haptics.success.bind(haptics),
    warning: haptics.warning.bind(haptics),
    error: haptics.error.bind(haptics),
    selection: haptics.selection.bind(haptics),
    notification: haptics.notification.bind(haptics),
    setEnabled: haptics.setEnabled.bind(haptics),
    isEnabled: haptics.isEnabled.bind(haptics),
  };
};

/**
 * Haptic feedback presets for common interactions
 */
export const HapticPresets = {
  // Button interactions
  buttonPress: () => haptics.light(),
  buttonLongPress: () => haptics.medium(),
  
  // Navigation
  tabSwitch: () => haptics.selection(),
  backNavigation: () => haptics.light(),
  
  // Form interactions
  textInput: () => haptics.light(),
  toggleSwitch: () => haptics.selection(),
  sliderChange: () => haptics.selection(),
  
  // Capture moments
  captureStart: () => haptics.medium(),
  captureComplete: () => haptics.success(),
  captureError: () => haptics.error(),
  
  // List interactions
  itemSelect: () => haptics.selection(),
  itemDelete: () => haptics.warning(),
  itemComplete: () => haptics.success(),
  
  // Settings
  settingChange: () => haptics.selection(),
  settingSave: () => haptics.success(),
  settingReset: () => haptics.warning(),
  
  // Errors and warnings
  validationError: () => haptics.error(),
  networkError: () => haptics.error(),
  warningMessage: () => haptics.warning(),
  
  // Success states
  loginSuccess: () => haptics.success(),
  uploadComplete: () => haptics.success(),
  syncComplete: () => haptics.success(),
  
  // Loading states
  loadingStart: () => haptics.light(),
  loadingComplete: () => haptics.medium(),
};

/**
 * Higher-order component for adding haptic feedback to TouchableOpacity
 */
export const addHapticFeedback = (
  onPress: () => void,
  hapticType: HapticType = 'light'
) => {
  return () => {
    haptics.trigger(hapticType);
    onPress();
  };
};

export default {
  haptics,
  useHaptics,
  HapticPresets,
  addHapticFeedback,
};
