/**
 * Component Library and Palette System
 * 
 * Provides a comprehensive component library with drag-and-drop palette,
 * component organization, and real-time search capabilities.
 * 
 * Features:
 * - 📦 Component library management
 * - 🎨 Visual component palette
 * - 🔍 Real-time search and filtering
 * - 🏷️ Component categorization and tagging
 * - 👥 Collaborative component sharing
 * - 📊 Component usage tracking
 * 
 * @doc.type system
 * @doc.purpose Component library and palette
 * @doc.layer product
 * @doc.pattern Library
 */

/**
 * Component metadata
 */
export interface ComponentMetadata {
  id: string;
  name: string;
  category: string;
  tags: string[];
  description: string;
  preview?: string;
  icon?: string;
  usageCount: number;
  lastUsed?: number;
  isCustom: boolean;
  author?: string;
}

/**
 * Component definition
 */
export interface ComponentDefinition {
  id: string;
  type: string;
  name: string;
  metadata: ComponentMetadata;
  defaultProps: Record<string, unknown>;
  defaultStyles: Record<string, string>;
  children?: ComponentDefinition[];
  validation?: {
    requiredProps?: string[];
    propTypes?: Record<string, string>;
  };
}

/**
 * Component category
 */
export interface ComponentCategory {
  id: string;
  name: string;
  description: string;
  icon: string;
  components: ComponentDefinition[];
  subcategories?: ComponentCategory[];
}

/**
 * Component Library
 */
export class ComponentLibrary {
  private components: Map<string, ComponentDefinition> = new Map();
  private categories: Map<string, ComponentCategory> = new Map();
  private searchIndex: Map<string, Set<string>> = new Map();
  private listeners: Set<(library: ComponentLibrary) => void> = new Set();

  constructor(initialComponents: ComponentDefinition[] = []) {
    initialComponents.forEach((component) => {
      this.addComponent(component);
    });
  }

  /**
   * Add component to library
   */
  addComponent(component: ComponentDefinition): void {
    this.components.set(component.id, component);
    this.updateSearchIndex(component);
    this.notifyListeners();
  }

  /**
   * Remove component from library
   */
  removeComponent(componentId: string): void {
    this.components.delete(componentId);
    this.notifyListeners();
  }

  /**
   * Get component by ID
   */
  getComponent(componentId: string): ComponentDefinition | undefined {
    return this.components.get(componentId);
  }

  /**
   * Get all components
   */
  getAllComponents(): ComponentDefinition[] {
    return Array.from(this.components.values());
  }

  /**
   * Add category
   */
  addCategory(category: ComponentCategory): void {
    this.categories.set(category.id, category);
    this.notifyListeners();
  }

  /**
   * Get category
   */
  getCategory(categoryId: string): ComponentCategory | undefined {
    return this.categories.get(categoryId);
  }

  /**
   * Get all categories
   */
  getAllCategories(): ComponentCategory[] {
    return Array.from(this.categories.values());
  }

  /**
   * Search components
   */
  searchComponents(query: string): ComponentDefinition[] {
    const lowerQuery = query.toLowerCase();
    
    return this.getAllComponents().filter((component) => {
      const matchesName = component.name.toLowerCase().includes(lowerQuery);
      const matchesDescription = component.metadata.description.toLowerCase().includes(lowerQuery);
      const matchesTags = component.metadata.tags.some((tag) =>
        tag.toLowerCase().includes(lowerQuery)
      );
      
      return matchesName || matchesDescription || matchesTags;
    });
  }

  /**
   * Get components by category
   */
  getComponentsByCategory(categoryId: string): ComponentDefinition[] {
    const category = this.getCategory(categoryId);
    if (!category) return [];
    
    return category.components;
  }

  /**
   * Get components by tag
   */
  getComponentsByTag(tag: string): ComponentDefinition[] {
    return this.getAllComponents().filter((component) =>
      component.metadata.tags.includes(tag)
    );
  }

  /**
   * Get popular components
   */
  getPopularComponents(limit = 10): ComponentDefinition[] {
    return this.getAllComponents()
      .sort((a, b) => b.metadata.usageCount - a.metadata.usageCount)
      .slice(0, limit);
  }

  /**
   * Get recently used components
   */
  getRecentlyUsedComponents(limit = 10): ComponentDefinition[] {
    return this.getAllComponents()
      .filter((c) => c.metadata.lastUsed)
      .sort((a, b) => (b.metadata.lastUsed || 0) - (a.metadata.lastUsed || 0))
      .slice(0, limit);
  }

  /**
   * Track component usage
   */
  trackComponentUsage(componentId: string): void {
    const component = this.getComponent(componentId);
    if (component) {
      component.metadata.usageCount++;
      component.metadata.lastUsed = Date.now();
      this.notifyListeners();
    }
  }

  /**
   * Update search index
   */
  private updateSearchIndex(component: ComponentDefinition): void {
    const keywords = [
      component.name.toLowerCase(),
      component.type.toLowerCase(),
      ...component.metadata.tags.map((tag) => tag.toLowerCase()),
    ];

    keywords.forEach((keyword) => {
      if (!this.searchIndex.has(keyword)) {
        this.searchIndex.set(keyword, new Set());
      }
      this.searchIndex.get(keyword)!.add(component.id);
    });
  }

  /**
   * Subscribe to library changes
   */
  subscribe(listener: (library: ComponentLibrary) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Notify listeners of changes
   */
  private notifyListeners(): void {
    this.listeners.forEach((listener) => listener(this));
  }

  /**
   * Export library as JSON
   */
  export(): string {
    return JSON.stringify({
      components: this.getAllComponents(),
      categories: this.getAllCategories(),
    });
  }

  /**
   * Import library from JSON
   */
  import(json: string): void {
    try {
      const data = JSON.parse(json);
      
      if (data.components) {
        data.components.forEach((component: ComponentDefinition) => {
          this.addComponent(component);
        });
      }
      
      if (data.categories) {
        data.categories.forEach((category: ComponentCategory) => {
          this.addCategory(category);
        });
      }
    } catch (error) {
      console.error('Failed to import library:', error);
    }
  }
}

/**
 * Default component library with common UI components
 */
export function createDefaultComponentLibrary(): ComponentLibrary {
  const library = new ComponentLibrary();

  // Basic Components Category
  const basicCategory: ComponentCategory = {
    id: 'basic',
    name: 'Basic Components',
    description: 'Fundamental UI components',
    icon: '📦',
    components: [
      {
        id: 'button',
        type: 'Button',
        name: 'Button',
        metadata: {
          id: 'button',
          name: 'Button',
          category: 'basic',
          tags: ['interaction', 'action'],
          description: 'Interactive button component',
          usageCount: 0,
          isCustom: false,
        },
        defaultProps: {
          label: 'Click me',
          onClick: () => {},
        },
        defaultStyles: {
          padding: '8px 16px',
          backgroundColor: '#007bff',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer',
        },
      },
      {
        id: 'text',
        type: 'Text',
        name: 'Text',
        metadata: {
          id: 'text',
          name: 'Text',
          category: 'basic',
          tags: ['typography', 'content'],
          description: 'Text display component',
          usageCount: 0,
          isCustom: false,
        },
        defaultProps: {
          content: 'Sample text',
        },
        defaultStyles: {
          fontSize: '16px',
          color: '#333',
        },
      },
      {
        id: 'input',
        type: 'Input',
        name: 'Input',
        metadata: {
          id: 'input',
          name: 'Input',
          category: 'basic',
          tags: ['form', 'input'],
          description: 'Text input component',
          usageCount: 0,
          isCustom: false,
        },
        defaultProps: {
          placeholder: 'Enter text...',
          onChange: () => {},
        },
        defaultStyles: {
          padding: '8px 12px',
          border: '1px solid #ddd',
          borderRadius: '4px',
          fontSize: '14px',
        },
      },
    ],
  };

  library.addCategory(basicCategory);

  // Layout Components Category
  const layoutCategory: ComponentCategory = {
    id: 'layout',
    name: 'Layout Components',
    description: 'Layout and structure components',
    icon: '📐',
    components: [
      {
        id: 'container',
        type: 'Container',
        name: 'Container',
        metadata: {
          id: 'container',
          name: 'Container',
          category: 'layout',
          tags: ['layout', 'wrapper'],
          description: 'Container for grouping elements',
          usageCount: 0,
          isCustom: false,
        },
        defaultProps: {},
        defaultStyles: {
          display: 'flex',
          flexDirection: 'column',
          padding: '16px',
          gap: '8px',
        },
      },
      {
        id: 'grid',
        type: 'Grid',
        name: 'Grid',
        metadata: {
          id: 'grid',
          name: 'Grid',
          category: 'layout',
          tags: ['layout', 'grid'],
          description: 'Grid layout component',
          usageCount: 0,
          isCustom: false,
        },
        defaultProps: {
          columns: 3,
        },
        defaultStyles: {
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '16px',
        },
      },
    ],
  };

  library.addCategory(layoutCategory);

  return library;
}

/**
 * Create component from library definition
 */
export function createComponentFromDefinition(definition: ComponentDefinition): unknown {
  return {
    id: definition.id,
    type: definition.type,
    name: definition.name,
    props: definition.defaultProps,
    styles: definition.defaultStyles,
  };
}
