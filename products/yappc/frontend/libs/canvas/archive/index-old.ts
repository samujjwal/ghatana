// Advanced UX & Accessibility system - Phase 10: User Experience Enhancement
// Comprehensive UX improvements with command palette, accessibility, and keyboard shortcuts

import React from 'react';

import { useCommandPalette, useAccessibility, useKeyboardShortcuts } from './hooks';

// Core exports
export * from './hooks';
export * from './components';

// Re-export the main hook types from hooks.ts (they are the authoritative types)
export type {
  Command,
  CommandCategory,
  AccessibilityConfig,
  AccessibilityAnnouncement,
  KeyboardShortcut,
} from './hooks';

// Component integration examples
export const UXSystemProvider = {
  // Command palette integration
  commandPaletteIntegration: `
    import { CommandPalette, useCommandPalette, createDefaultCommands } from '@your-org/canvas/ux';
    
    const MyApp = () => {
      const [showPalette, setShowPalette] = useState(false);
      const commands = createDefaultCommands();
      
      const {
        registerCommand,
        executeCommand,
      } = useCommandPalette({
        commands,
      });
      
      // Register custom commands
      useEffect(() => {
        registerCommand({
          id: 'my-custom-command',
          title: 'My Custom Action',
          description: 'Perform a custom action',
          category: 'custom',
          keywords: ['custom', 'action'],
          shortcut: 'Ctrl + Shift + C',
          action: () => console.log('Custom action executed'),
        });
      }, [registerCommand]);
      
      return (
        <div>
          <button onClick={() => setShowPalette(true)}>
            Open Command Palette (Ctrl + Shift + P)
          </button>
          
          <CommandPalette
            open={showPalette}
            onClose={() => setShowPalette(false)}
            commands={commands}
          />
        </div>
      );
    };
  `,
  
  // Accessibility integration
  accessibilityIntegration: `
    import { AccessibilityPanel, useAccessibility } from '@your-org/canvas/ux';
    
    const MyAccessibleComponent = () => {
      const {
        config,
        updateConfig,
        announce,
        focusElement,
        handleKeyboardNavigation,
        setAriaLabel,
      } = useAccessibility({
        initialConfig: {
          enableScreenReader: true,
          enableKeyboardNavigation: true,
          fontSize: 'medium',
        },
      });
      
      const handleButtonClick = () => {
        announce('Button was clicked', 'polite');
        // Perform action
      };
      
      const handleKeyDown = (event) => {
        handleKeyboardNavigation(event, {
          onEnter: handleButtonClick,
          onEscape: () => announce('Action cancelled'),
          onArrowDown: () => focusElement('#next-button'),
        });
      };
      
      return (
        <div>
          <button
            onClick={handleButtonClick}
            onKeyDown={handleKeyDown}
            ref={(el) => el && setAriaLabel(el, 'Click to perform action')}
          >
            Accessible Button
          </button>
        </div>
      );
    };
  `,
  
  // Keyboard shortcuts integration
  keyboardShortcutsIntegration: `
    import { KeyboardShortcutsHelp, useKeyboardShortcuts } from '@your-org/canvas/ux';
    
    const MyKeyboardApp = () => {
      const {
        registerShortcut,
        getShortcutString,
        shortcuts,
      } = useKeyboardShortcuts({
        enabled: true,
        context: 'global',
      });
      
      useEffect(() => {
        // Register application shortcuts
        registerShortcut({
          id: 'save-document',
          keys: ['ctrl', 's'],
          description: 'Save current document',
          category: 'file',
          action: (event) => {
            event.preventDefault();
            console.log('Saving document...');
          },
        });
        
        registerShortcut({
          id: 'open-help',
          keys: ['f1'],
          description: 'Open help documentation',
          category: 'help',
          action: () => {
            setShowHelp(true);
          },
        });
      }, [registerShortcut]);
      
      return (
        <div>
          <h1>My Application</h1>
          <p>Press {getShortcutString(['ctrl', 's'])} to save</p>
          <p>Press {getShortcutString(['f1'])} for help</p>
          
          <KeyboardShortcutsHelp
            open={showHelp}
            onClose={() => setShowHelp(false)}
            shortcuts={shortcuts}
          />
        </div>
      );
    };
  `,
  
  // Complete UX settings integration
  completeUXIntegration: `
    import { 
      UXSettings, 
      useAccessibility, 
      useKeyboardShortcuts,
      createDefaultUserPreferences,
    } from '@your-org/canvas/ux';
    
    const MyUXEnabledApp = () => {
      const [showSettings, setShowSettings] = useState(false);
      const [userPrefs, setUserPrefs] = useState(createDefaultUserPreferences());
      
      const accessibility = useAccessibility({
        initialConfig: userPrefs.accessibility,
      });
      
      const shortcuts = useKeyboardShortcuts({
        shortcuts: userPrefs.shortcuts,
        enabled: true,
      });
      
      const handleAccessibilityChange = (newConfig) => {
        setUserPrefs(prev => ({
          ...prev,
          accessibility: newConfig,
        }));
        
        // Save to localStorage or API
        localStorage.setItem('userPreferences', JSON.stringify({
          ...userPrefs,
          accessibility: newConfig,
        }));
  `,
};
