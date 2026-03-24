import { describe, it, expect } from 'vitest';

import { 
  runAccessibilityAudit, 
  getA11yProps, 
  createA11yLabel,
  accessibilityRules
} from '../accessibility';

describe('Accessibility Utilities', () => {
  describe('getA11yProps', () => {
    it('extracts accessibility props correctly', () => {
      const props = {
        'aria-label': 'Test Label',
        'aria-labelledby': 'test-id',
        'aria-describedby': 'description-id',
        'aria-hidden': false,
        'aria-live': 'polite',
        role: 'button',
        tabIndex: 0,
        className: 'test-class',
        style: { color: 'red' },
      };
      
      const { a11yProps, rest } = getA11yProps(props);
      
      expect(a11yProps).toEqual({
        'aria-label': 'Test Label',
        'aria-labelledby': 'test-id',
        'aria-describedby': 'description-id',
        'aria-hidden': false,
        'aria-live': 'polite',
        role: 'button',
        tabIndex: 0,
      });
      
      expect(rest).toEqual({
        className: 'test-class',
        style: { color: 'red' },
      });
    });
    
    it('handles empty props', () => {
      const props = {};
      
      const { a11yProps, rest } = getA11yProps(props);
      
      expect(a11yProps).toEqual({});
      expect(rest).toEqual({});
    });
    
    it('handles props without accessibility attributes', () => {
      const props = {
        className: 'test-class',
        style: { color: 'red' },
      };
      
      const { a11yProps, rest } = getA11yProps(props);
      
      expect(a11yProps).toEqual({});
      expect(rest).toEqual({
        className: 'test-class',
        style: { color: 'red' },
      });
    });
  });
  
  describe('createA11yLabel', () => {
    it('creates label and element props correctly', () => {
      const { labelProps, elementProps } = createA11yLabel('Email', 'email-input');
      
      expect(labelProps).toEqual({
        id: 'email-input-label',
        htmlFor: 'email-input',
      });
      
      expect(elementProps).toEqual({
        id: 'email-input',
        'aria-labelledby': 'email-input-label',
      });
    });
  });
  
  describe('accessibilityRules', () => {
    it('has rules defined', () => {
      expect(accessibilityRules.length).toBeGreaterThan(0);
    });
    
    it('has image alt text rule', () => {
      const imgAltRule = accessibilityRules.find(rule => rule.id === 'a11y-img-alt');
      expect(imgAltRule).toBeDefined();
      expect(imgAltRule?.name).toBe('Image Alt Text');
    });
    
    it('has button name rule', () => {
      const buttonNameRule = accessibilityRules.find(rule => rule.id === 'a11y-button-name');
      expect(buttonNameRule).toBeDefined();
      expect(buttonNameRule?.name).toBe('Button Name');
    });
    
    it('tests image alt text rule correctly', () => {
      const imgAltRule = accessibilityRules.find(rule => rule.id === 'a11y-img-alt');
      
      // Create test elements
      const imgWithAlt = document.createElement('img');
      imgWithAlt.setAttribute('alt', 'Test image');
      
      const imgWithoutAlt = document.createElement('img');
      
      const nonImgElement = document.createElement('div');
      
      // Test the rule
      expect(imgAltRule?.test(imgWithAlt)).toBe(true);
      expect(imgAltRule?.test(imgWithoutAlt)).toBe(false);
      expect(imgAltRule?.test(nonImgElement)).toBe(true);
    });
    
    it('tests button name rule correctly', () => {
      const buttonNameRule = accessibilityRules.find(rule => rule.id === 'a11y-button-name');
      
      // Create test elements
      const buttonWithText = document.createElement('button');
      buttonWithText.textContent = 'Click me';
      
      const buttonWithAriaLabel = document.createElement('button');
      buttonWithAriaLabel.setAttribute('aria-label', 'Click me');
      
      const buttonWithAriaLabelledby = document.createElement('button');
      buttonWithAriaLabelledby.setAttribute('aria-labelledby', 'label-id');
      
      const buttonWithoutName = document.createElement('button');
      
      const divWithRoleButton = document.createElement('div');
      divWithRoleButton.setAttribute('role', 'button');
      divWithRoleButton.textContent = 'Click me';
      
      const divWithRoleButtonWithoutName = document.createElement('div');
      divWithRoleButtonWithoutName.setAttribute('role', 'button');
      
      const nonButtonElement = document.createElement('div');
      
      // Test the rule
      expect(buttonNameRule?.test(buttonWithText)).toBe(true);
      expect(buttonNameRule?.test(buttonWithAriaLabel)).toBe(true);
      expect(buttonNameRule?.test(buttonWithAriaLabelledby)).toBe(true);
      expect(buttonNameRule?.test(buttonWithoutName)).toBe(false);
      expect(buttonNameRule?.test(divWithRoleButton)).toBe(true);
      expect(buttonNameRule?.test(divWithRoleButtonWithoutName)).toBe(false);
      expect(buttonNameRule?.test(nonButtonElement)).toBe(true);
    });
  });
  
  describe('runAccessibilityAudit', () => {
    it('returns audit result with component name', () => {
      const element = document.createElement('div');
      const result = runAccessibilityAudit(element, 'TestComponent');
      
      expect(result).toHaveProperty('component', 'TestComponent');
      expect(result).toHaveProperty('passed');
      expect(result).toHaveProperty('issues');
    });
    
    it('passes audit for accessible element', () => {
      const element = document.createElement('div');
      element.setAttribute('role', 'region');
      element.setAttribute('aria-label', 'Test Region');
      
      const result = runAccessibilityAudit(element, 'TestComponent');
      
      expect(result.passed).toBe(true);
      expect(result.issues.length).toBe(0);
    });
    
    it('fails audit for inaccessible element', () => {
      const element = document.createElement('div');
      const img = document.createElement('img');
      element.appendChild(img);
      
      const result = runAccessibilityAudit(element, 'TestComponent');
      
      expect(result.passed).toBe(false);
      expect(result.issues.length).toBeGreaterThan(0);
      
      // Check that the image alt text issue is reported
      const imgAltIssue = result.issues.find(issue => issue.code === 'a11y-img-alt');
      expect(imgAltIssue).toBeDefined();
    });
    
    it('audits nested elements', () => {
      const element = document.createElement('div');
      
      const button = document.createElement('button');
      element.appendChild(button);
      
      const result = runAccessibilityAudit(element, 'TestComponent');
      
      // Check that the button name issue is reported
      const buttonNameIssue = result.issues.find(issue => issue.code === 'a11y-button-name');
      expect(buttonNameIssue).toBeDefined();
    });
  });
});
