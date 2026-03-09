/**
 * @ghatana/yappc-ide - Integration Tests
 * 
 * Basic integration tests for IDE functionality
 */

// Using Jest test globals (describe/it/expect/beforeEach) - originally written for Vitest

import { createFile, createFolder, detectLanguage, isFile, isFolder } from '../utils/fileSystem';
import { createTab, addTab, removeTab, setActiveTab, getActiveTab } from '../utils/tabManager';
import type { IDEFile, IDEFolder, IDETab } from '../types';

describe('IDE Integration Tests', () => {
  describe('File System Utilities', () => {
    it('should create a file with UUID', () => {
      const file = createFile('/src/index.ts', 'console.log("hello")', 'typescript');

      expect(file).toBeDefined();
      expect(file.id).toBeDefined();
      expect(file.path).toBe('/src/index.ts');
      expect(file.name).toBe('index.ts');
      expect(file.content).toBe('console.log("hello")');
      expect(file.language).toBe('typescript');
      expect(file.isDirty).toBe(false);
      expect(file.isOpen).toBe(false);
    });

    it('should detect language from file extension', () => {
      expect(detectLanguage('index.ts')).toBe('typescript');
      expect(detectLanguage('index.js')).toBe('javascript');
      expect(detectLanguage('index.py')).toBe('python');
      expect(detectLanguage('index.java')).toBe('java');
      expect(detectLanguage('index.go')).toBe('go');
      expect(detectLanguage('index.unknown')).toBe('plaintext');
    });

    it('should create a folder with UUID', () => {
      const folder = createFolder('/src');

      expect(folder).toBeDefined();
      expect(folder.id).toBeDefined();
      expect(folder.path).toBe('/src');
      expect(folder.name).toBe('src');
      expect(folder.children).toEqual([]);
      expect(folder.isExpanded).toBe(false);
    });

    it('should identify files and folders correctly', () => {
      const file = createFile('/src/index.ts', '', 'typescript');
      const folder = createFolder('/src');

      expect(isFile(file)).toBe(true);
      expect(isFolder(file)).toBe(false);
      expect(isFile(folder)).toBe(false);
      expect(isFolder(folder)).toBe(true);
    });
  });

  describe('Tab Manager', () => {
    let tabs: IDETab[];

    beforeEach(() => {
      tabs = [];
    });

    it('should create a tab', () => {
      const tab = createTab('file-id-1', 'index.ts');

      expect(tab).toBeDefined();
      expect(tab.id).toBeDefined();
      expect(tab.fileId).toBe('file-id-1');
      expect(tab.title).toBe('index.ts');
      expect(tab.isDirty).toBe(false);
      expect(tab.isActive).toBe(false);
      expect(tab.isPinned).toBe(false);
    });

    it('should add a tab and make it active', () => {
      const tab = createTab('file-id-1', 'index.ts');
      tabs = addTab(tabs, tab);

      expect(tabs).toHaveLength(1);
      expect(tabs[0].isActive).toBe(true);
    });

    it('should activate existing tab instead of adding duplicate', () => {
      const tab1 = createTab('file-id-1', 'index.ts');
      const tab2 = createTab('file-id-1', 'index.ts');

      tabs = addTab(tabs, tab1);
      tabs = addTab(tabs, tab2);

      expect(tabs).toHaveLength(1);
      expect(tabs[0].isActive).toBe(true);
    });

    it('should remove a tab', () => {
      const tab1 = createTab('file-id-1', 'index.ts');
      const tab2 = createTab('file-id-2', 'app.ts');

      tabs = addTab(tabs, tab1);
      tabs = addTab(tabs, tab2);
      expect(tabs).toHaveLength(2);

      tabs = removeTab(tabs, tab1.id);
      expect(tabs).toHaveLength(1);
      expect(tabs[0].fileId).toBe('file-id-2');
    });

    it('should set active tab', () => {
      const tab1 = createTab('file-id-1', 'index.ts');
      const tab2 = createTab('file-id-2', 'app.ts');

      tabs = addTab(tabs, tab1);
      tabs = addTab(tabs, tab2);

      tabs = setActiveTab(tabs, tab1.id);

      expect(tabs[0].isActive).toBe(true);
      expect(tabs[1].isActive).toBe(false);
    });

    it('should get active tab', () => {
      const tab1 = createTab('file-id-1', 'index.ts');
      const tab2 = createTab('file-id-2', 'app.ts');

      tabs = addTab(tabs, tab1);
      tabs = addTab(tabs, tab2);

      const activeTab = getActiveTab(tabs);
      expect(activeTab).toBeDefined();
      expect(activeTab?.fileId).toBe('file-id-2');
    });

    it('should mark tab as dirty', () => {
      const tab = createTab('file-id-1', 'index.ts');
      tabs = addTab(tabs, tab);

      expect(tabs[0].isDirty).toBe(false);

      // Simulate marking as dirty (would be done via hook in real app)
      tabs = tabs.map(t => t.id === tab.id ? { ...t, isDirty: true } : t);

      expect(tabs[0].isDirty).toBe(true);
    });
  });

  describe('IDE State Integration', () => {
    it('should handle file creation workflow', () => {
      // Create a file
      const file = createFile('/src/index.ts', 'console.log("hello")', 'typescript');

      // Create a tab for the file
      const tab = createTab(file.id, file.name);
      let tabs: IDETab[] = [];
      tabs = addTab(tabs, tab);

      // Verify state
      expect(file.id).toBeDefined();
      expect(tabs).toHaveLength(1);
      expect(tabs[0].fileId).toBe(file.id);
      expect(tabs[0].isActive).toBe(true);
    });

    it('should handle multiple file workflow', () => {
      const files: IDEFile[] = [];
      let tabs: IDETab[] = [];

      // Create multiple files
      for (let i = 0; i < 3; i++) {
        const file = createFile(`/src/file${i}.ts`, `content${i}`, 'typescript');
        files.push(file);

        const tab = createTab(file.id, file.name);
        tabs = addTab(tabs, tab);
      }

      expect(files).toHaveLength(3);
      expect(tabs).toHaveLength(3);
      expect(tabs[tabs.length - 1].isActive).toBe(true);
    });

    it('should handle tab switching workflow', () => {
      const files: IDEFile[] = [];
      let tabs: IDETab[] = [];

      // Create files and tabs
      const file1 = createFile('/src/index.ts', 'content1', 'typescript');
      const file2 = createFile('/src/app.ts', 'content2', 'typescript');
      files.push(file1, file2);

      const tab1 = createTab(file1.id, file1.name);
      const tab2 = createTab(file2.id, file2.name);

      tabs = addTab(tabs, tab1);
      tabs = addTab(tabs, tab2);

      // Verify tab2 is active
      expect(getActiveTab(tabs)?.fileId).toBe(file2.id);

      // Switch to tab1
      tabs = setActiveTab(tabs, tab1.id);
      expect(getActiveTab(tabs)?.fileId).toBe(file1.id);
    });
  });
});
