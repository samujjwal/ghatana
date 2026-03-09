import { testThemeAccessibility, calculateContrastRatio } from '../testing';
import { lightTheme, darkTheme } from '../theme';

describe('Theme Accessibility', () => {
  describe('Light Theme', () => {
    it('should have sufficient contrast for text on background', () => {
      const results = testThemeAccessibility('light', 'AA');
      
      // Check text on default background
      expect(results.primary_on_default.pass).toBe(true);
      expect(results.secondary_on_default.pass).toBe(true);
      
      // Check text on paper background
      expect(results.primary_on_paper.pass).toBe(true);
      expect(results.secondary_on_paper.pass).toBe(true);
    });
    
    it('should have sufficient contrast for button text', () => {
      const results = testThemeAccessibility('light', 'AA');
      
      expect(results.text_on_primary_button.pass).toBe(true);
      expect(results.text_on_secondary_button.pass).toBe(true);
      expect(results.text_on_error_button.pass).toBe(true);
      expect(results.text_on_warning_button.pass).toBe(true);
      expect(results.text_on_info_button.pass).toBe(true);
      expect(results.text_on_success_button.pass).toBe(true);
    });
    
    it('should meet stricter AAA standards for critical text', () => {
      // Test primary text on background with AAA standards
      const ratio = calculateContrastRatio(
        lightTheme.palette.text.primary,
        lightTheme.palette.background.default
      );
      
      // AAA requires 7:1 for normal text
      expect(ratio).toBeGreaterThanOrEqual(7);
    });
  });
  
  describe('Dark Theme', () => {
    it('should have sufficient contrast for text on background', () => {
      const results = testThemeAccessibility('dark', 'AA');
      
      // Check text on default background
      expect(results.primary_on_default.pass).toBe(true);
      expect(results.secondary_on_default.pass).toBe(true);
      
      // Check text on paper background
      expect(results.primary_on_paper.pass).toBe(true);
      expect(results.secondary_on_paper.pass).toBe(true);
    });
    
    it('should have sufficient contrast for button text', () => {
      const results = testThemeAccessibility('dark', 'AA');
      
      expect(results.text_on_primary_button.pass).toBe(true);
      expect(results.text_on_secondary_button.pass).toBe(true);
      expect(results.text_on_error_button.pass).toBe(true);
      expect(results.text_on_warning_button.pass).toBe(true);
      expect(results.text_on_info_button.pass).toBe(true);
      expect(results.text_on_success_button.pass).toBe(true);
    });
    
    it('should meet stricter AAA standards for critical text', () => {
      // Test primary text on background with AAA standards
      const ratio = calculateContrastRatio(
        darkTheme.palette.text.primary,
        darkTheme.palette.background.default
      );
      
      // AAA requires 7:1 for normal text
      expect(ratio).toBeGreaterThanOrEqual(7);
    });
  });
  
  describe('Contrast Ratio Calculation', () => {
    it('should calculate correct contrast ratios', () => {
      // White on black (maximum contrast)
      expect(calculateContrastRatio('#ffffff', '#000000')).toBeCloseTo(21, 0);
      
      // Black on white (maximum contrast)
      expect(calculateContrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 0);
      
      // Medium gray on white (moderate contrast)
      expect(calculateContrastRatio('#808080', '#ffffff')).toBeGreaterThan(3);
      expect(calculateContrastRatio('#808080', '#ffffff')).toBeLessThan(10);
      
      // Similar colors (low contrast)
      expect(calculateContrastRatio('#eeeeee', '#ffffff')).toBeLessThan(2);
    });
  });
});
