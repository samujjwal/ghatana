#!/usr/bin/env node

/**
 * PHR Route-Level WCAG Checklist Generator
 *
 * Generates a WCAG 2.1 AA compliance checklist for each route/screen
 * based on the PHR use-case baseline. This helps ensure accessibility
 * requirements are tracked per IA item.
 *
 * Usage:
 *   node scripts/generate-phr-wcag-checklist.mjs
 */

import { readFileSync, writeFileSync } from 'fs';
import { join, resolve } from 'path';
import { fileURLToPath } from 'url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const BASELINE_FILE = resolve(__dirname, '../products/phr/config/phr-usecase-baseline.json');
const OUTPUT_FILE = resolve(__dirname, '../products/phr/docs/phr-wcag-checklist.md');

// WCAG 2.1 AA Success Criteria mapped to common UI patterns
const WCAG_CRITERIA = {
  '1.1.1 Non-text Content': {
    description: 'All non-text content has a text alternative',
    checks: ['Images have alt text', 'Icons have labels', 'Charts have data tables'],
  },
  '1.3.1 Info and Relationships': {
    description: 'Information, structure, and relationships can be programmatically determined',
    checks: ['Semantic HTML used', 'Headings nested correctly', 'Lists marked as lists'],
  },
  '1.3.2 Meaningful Sequence': {
    description: 'The meaning of content can be determined from the order of presentation',
    checks: ['Reading order matches visual order', 'CSS reordering handled correctly'],
  },
  '1.3.3 Sensory Characteristics': {
    description: 'Instructions do not rely solely on sensory characteristics',
    checks: ['Not just "click the green button"', 'Not just "the field on the right"'],
  },
  '1.3.4 Orientation': {
    description: 'Content does not restrict its view and operation to a single display orientation',
    checks: ['Works in portrait and landscape', 'No rotation lock required'],
  },
  '1.3.5 Identify Input Purpose': {
    description: 'The purpose of each input field can be programmatically determined',
    checks: ['autocomplete attributes used', 'input types match purpose'],
  },
  '1.4.1 Use of Color': {
    description: 'Color is not used as the only visual means of conveying information',
    checks: ['Icons used with color', 'Text labels used with color indicators'],
  },
  '1.4.3 Contrast (Minimum)': {
    description: 'Text and images of text have a contrast ratio of at least 4.5:1',
    checks: ['Normal text: 4.5:1 contrast', 'Large text: 3:1 contrast', 'UI components: 3:1 contrast'],
  },
  '1.4.10 Reflow': {
    description: 'Content can be presented without loss of information or functionality',
    checks: ['No horizontal scroll at 400% zoom', 'Content reflows vertically'],
  },
  '1.4.11 Non-text Contrast': {
    description: 'The visual presentation of UI components has a contrast ratio of at least 3:1',
    checks: ['Buttons have sufficient contrast', 'Form fields have visible borders'],
  },
  '1.4.12 Text Spacing': {
    description: 'Text spacing can be adjusted without loss of content',
    checks: ['Line height can be increased', 'Letter spacing can be increased'],
  },
  '1.4.13 Content on Hover or Focus': {
    description: 'Content on hover or focus can be dismissed without moving pointer',
    checks: ['Tooltips dismissible', 'Menus dismissible with ESC'],
  },
  '2.1.1 Keyboard': {
    description: 'All functionality is available using a keyboard',
    checks: ['No keyboard traps', 'Focus order logical', 'Skip links provided'],
  },
  '2.1.2 No Keyboard Trap': {
    description: 'Keyboard focus can be moved away from any component',
    checks: ['Modal can be closed with ESC', 'Focus returns to trigger'],
  },
  '2.1.4 Character Key Shortcuts': {
    description: 'Character key shortcuts can be turned off or remapped',
    checks: ['Single-key shortcuts avoidable', 'Shortcuts documented'],
  },
  '2.2.1 Timing Adjustable': {
    description: 'Users are given enough time to read and use content',
    checks: ['No auto-redirect without warning', 'Sessions can be extended'],
  },
  '2.3.1 Three Flashes or Below': {
    description: 'Web pages do not contain anything that flashes more than three times per second',
    checks: ['No flashing content', 'No strobing animations'],
  },
  '2.4.1 Bypass Blocks': {
    description: 'A mechanism is available to bypass blocks of content',
    checks: ['Skip to main content link', 'Skip navigation link'],
  },
  '2.4.2 Page Titled': {
    description: 'Web pages have titles that describe topic or purpose',
    checks: ['Unique page titles', 'Descriptive page titles'],
  },
  '2.4.3 Focus Order': {
    description: 'Focus order is logical and intuitive',
    checks: ['Tab order matches visual order', 'Focus visible'],
  },
  '2.4.4 Link Purpose': {
    description: 'The purpose of each link can be determined from the link text alone',
    checks: ['Descriptive link text', 'No "click here" links'],
  },
  '2.4.5 Multiple Ways': {
    description: 'Multiple ways are available to locate content',
    checks: ['Search available', 'Site map available', 'Navigation consistent'],
  },
  '2.4.6 Headings and Labels': {
    description: 'Headings and labels describe topic or purpose',
    checks: ['Page has h1', 'Headings used hierarchically', 'Form fields have labels'],
  },
  '2.4.7 Focus Visible': {
    description: 'Keyboard focus indicator is visible',
    checks: ['Focus indicator visible', 'Focus indicator contrast sufficient'],
  },
  '2.5.1 Pointer Gestures': {
    description: 'Functionality can be operated using pointer gestures with a single pointer',
    checks: ['No multi-touch required', 'Alternative to gestures provided'],
  },
  '2.5.2 Pointer Cancellation': {
    description: 'Functionality can be operated with various pointer inputs',
    checks: ['Down event does not trigger action', 'Up/Cancel events handled'],
  },
  '2.5.3 Label in Name': {
    description: 'For user interface components with labels that include text, the name contains the text',
    checks: ['Accessible name includes visible label', 'No redundant labels'],
  },
  '2.5.4 Motion Actuation': {
    description: 'Functionality operated by device motion can also be operated by user interface components',
    checks: ['Shake gesture has button alternative', 'Motion can be disabled'],
  },
  '2.5.5 Target Size': {
    description: 'The size of the target for pointer inputs is at least 44x44 CSS pixels',
    checks: ['Touch targets 44x44 minimum', 'Clickable areas sufficient'],
  },
  '3.1.1 Language of Page': {
    description: 'The default human language of each web page can be programmatically determined',
    checks: ['lang attribute on html', 'Correct language code'],
  },
  '3.2.1 On Focus': {
    description: 'When any component receives focus, it does not cause a change of context',
    checks: ['Focus does not trigger navigation', 'Focus does not open modals'],
  },
  '3.2.2 On Input': {
    description: 'Changing the setting of any user interface component does not automatically cause a change of context',
    checks: ['Select does not submit form', 'Checkbox does not trigger action'],
  },
  '3.3.1 Error Identification': {
    description: 'If an input error is automatically detected, the item is identified and the error is described',
    checks: ['Error messages specific', 'Error messages linked to field'],
  },
  '3.3.2 Labels or Instructions': {
    description: 'Labels or instructions are provided when content requires user input',
    checks: ['Form fields have labels', 'Required fields indicated'],
  },
  '3.3.3 Error Suggestion': {
    description: 'If an input error is automatically detected and suggestions for correction are known, suggestions are provided',
    checks: ['Suggestions provided for common errors', 'Format hints provided'],
  },
  '3.3.4 Error Prevention (Legal, Financial, Data)': {
    description: 'For web pages that cause legal commitments or financial transactions, reversible submissions are allowed',
    checks: ['Review before submit', 'Confirmation after submit', 'Cancel allowed'],
  },
  '4.1.1 Parsing': {
    description: 'In content implemented using markup languages, elements have complete start and end tags',
    checks: ['Valid HTML', 'Proper nesting', 'No duplicate IDs'],
  },
  '4.1.2 Name, Role, Value': {
    description: 'For all user interface components, the name and role can be programmatically determined',
    checks: ['ARIA roles correct', 'Accessible names present', 'States and properties correct'],
  },
  '4.1.3 Status Messages': {
    description: 'Status messages can be programmatically determined without receiving focus',
    checks: ['Live regions used', 'Alerts use role="alert"'],
  },
};

function main() {
  console.log('📋 Generating PHR WCAG checklist...\n');

  const baseline = JSON.parse(readFileSync(BASELINE_FILE, 'utf-8'));
  const usecases = baseline.usecases;

  let markdown = `# PHR WCAG 2.1 AA Compliance Checklist

**Generated:** ${new Date().toISOString().split('T')[0]}  
**Baseline Version:** ${baseline.version}  
**Total Routes/Screens:** ${usecases.length}

## Overview

This checklist provides WCAG 2.1 AA compliance requirements for each PHR route and screen. Use this to track accessibility testing and remediation.

## WCAG 2.1 AA Success Criteria

${Object.entries(WCAG_CRITERIA).map(([id, criteria]) => {
  return `
### ${id}: ${criteria.description}

**Checks:**
${criteria.checks.map(check => `- ${check}`).join('\n')}
`;
}).join('\n')}

## Route/Screen Checklist

${usecases.map(uc => {
  const routeInfo = uc.webRoute || uc.mobileScreen || uc.iaRoute || 'N/A';
  return `
### ${uc.id}: ${uc.screen} (${uc.persona})

**Route/Screen:** ${routeInfo}  
**Status:** ${uc.status}  
**Phase:** ${uc.phase}

#### WCAG Checklist

| Criterion | Status | Notes |
|-----------|--------|-------|
${Object.keys(WCAG_CRITERIA).map(criterion => {
  return `| ${criterion} | ⬜ | |`;
}).join('\n')}

#### Accessibility Notes

${uc.notes ? uc.notes : 'No specific accessibility notes.'}

---
`;
}).join('\n')}

## Testing Instructions

1. **Automated Testing:** Run axe-core or similar automated accessibility scanner
2. **Keyboard Testing:** Navigate each route using only keyboard (Tab, Enter, Esc, Arrow keys)
3. **Screen Reader Testing:** Test with NVDA (Windows) or VoiceOver (macOS/iOS)
4. **Color Contrast Testing:** Verify contrast ratios using axe or WebAIM Contrast Checker
5. **Mobile Testing:** Test with mobile screen reader (TalkBack on Android, VoiceOver on iOS)

## Remediation Priority

1. **P0 - Critical:** Keyboard traps, missing alt text, missing form labels, insufficient contrast
2. **P1 - High:** Focus order issues, heading structure, ARIA misuse
3. **P2 - Medium:** Skip links, page titles, error identification
4. **P3 - Low:** Enhanced features, advanced ARIA patterns

---

*This checklist is auto-generated from \`products/phr/config/phr-usecase-baseline.json\`*
`;

  writeFileSync(OUTPUT_FILE, markdown, 'utf-8');
  console.log(`✅ Generated WCAG checklist: ${OUTPUT_FILE}`);
  console.log(`📊 Processed ${usecases.length} routes/screens with ${Object.keys(WCAG_CRITERIA).length} WCAG criteria\n`);
}

main();
