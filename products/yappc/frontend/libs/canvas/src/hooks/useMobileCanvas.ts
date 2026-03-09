/**
 * useMobileCanvas Hook
 * 
 * React hook for managing mobile app screen design state.
 * Supports component management, platform switching, and code generation.
 * 
 * Features:
 * - Component CRUD operations
 * - Platform-specific rendering (iOS/Android)
 * - React Native code generation with Platform.select
 * - Device frame management
 * - Component property editing
 * 
 * @doc.type hook
 * @doc.purpose Mobile app screen design state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';
import type { Node } from '@xyflow/react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

/**
 * Mobile platform type
 */
export type MobilePlatform = 'ios' | 'android' | 'both';

/**
 * Mobile component types
 */
export type MobileComponentType =
  | 'navigation-bar'
  | 'toggle-switch'
  | 'slider'
  | 'list'
  | 'button'
  | 'text-input'
  | 'image'
  | 'card';

/**
 * Mobile component data
 */
export interface MobileComponent {
  id: string;
  type: MobileComponentType;
  label: string;
  props: Record<string, unknown>;
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Device frame dimensions
 */
export interface DeviceFrame {
  name: string;
  width: number;
  height: number;
  statusBarHeight: number;
  notchHeight?: number;
}

/**
 * Hook options
 */
export interface UseMobileCanvasOptions {
  /** Initial platform */
  initialPlatform?: MobilePlatform;
  /** Initial device */
  initialDevice?: string;
  /** Node context */
  node?: Node;
}

/**
 * Hook return type
 */
export interface UseMobileCanvasResult {
  // State
  platform: MobilePlatform;
  device: string;
  components: MobileComponent[];
  selectedComponent: string | null;
  deviceFrame: DeviceFrame;

  // Actions
  setPlatform: (platform: MobilePlatform) => void;
  setDevice: (device: string) => void;
  addComponent: (type: MobileComponentType, props?: Record<string, unknown>) => void;
  updateComponent: (id: string, updates: Partial<MobileComponent>) => void;
  deleteComponent: (id: string) => void;
  selectComponent: (id: string | null) => void;
  clearComponents: () => void;

  // Code Generation
  generateReactNativeCode: () => string;
  exportToFile: (code: string, filename: string) => void;

  // Utilities
  getComponentCount: (type?: MobileComponentType) => number;
  getComponentById: (id: string) => MobileComponent | undefined;
}

// ============================================================================
// DEVICE FRAMES
// ============================================================================

const DEVICE_FRAMES: Record<string, DeviceFrame> = {
  'iphone-14': {
    name: 'iPhone 14',
    width: 390,
    height: 844,
    statusBarHeight: 54,
    notchHeight: 30,
  },
  'iphone-14-pro': {
    name: 'iPhone 14 Pro',
    width: 393,
    height: 852,
    statusBarHeight: 59,
    notchHeight: 37,
  },
  'pixel-7': {
    name: 'Google Pixel 7',
    width: 412,
    height: 915,
    statusBarHeight: 48,
  },
  'samsung-s23': {
    name: 'Samsung Galaxy S23',
    width: 360,
    height: 800,
    statusBarHeight: 48,
  },
};

// Component defaults
const COMPONENT_DEFAULTS: Record<MobileComponentType, { props: Record<string, unknown>; size: { width: number; height: number } }> = {
  'navigation-bar': {
    props: { title: 'Screen Title' },
    size: { width: 360, height: 64 },
  },
  'toggle-switch': {
    props: { label: 'Enable Notifications', value: false },
    size: { width: 300, height: 48 },
  },
  'slider': {
    props: { label: 'Frequency', min: 0, max: 100, value: 50 },
    size: { width: 300, height: 64 },
  },
  'list': {
    props: { items: ['Item 1', 'Item 2', 'Item 3'] },
    size: { width: 360, height: 200 },
  },
  'button': {
    props: { title: 'Submit', variant: 'contained' },
    size: { width: 200, height: 48 },
  },
  'text-input': {
    props: { placeholder: 'Enter text...', value: '' },
    size: { width: 300, height: 48 },
  },
  'image': {
    props: { source: 'https://via.placeholder.com/150', alt: 'Placeholder' },
    size: { width: 150, height: 150 },
  },
  'card': {
    props: { title: 'Card Title', content: 'Card content' },
    size: { width: 340, height: 120 },
  },
};

// ============================================================================
// HOOK
// ============================================================================

/**
 * Hook for managing mobile canvas state
 */
export function useMobileCanvas(options: UseMobileCanvasOptions = {}): UseMobileCanvasResult {
  const { initialPlatform = 'ios', initialDevice = 'iphone-14', node } = options;

  // State
  const [platform, setPlatformState] = useState<MobilePlatform>(initialPlatform);
  const [device, setDevice] = useState<string>(initialDevice);
  const [components, setComponents] = useState<MobileComponent[]>([]);
  const [selectedComponent, setSelectedComponent] = useState<string | null>(null);

  // Get device frame
  const deviceFrame = useMemo(() => {
    return DEVICE_FRAMES[device] || DEVICE_FRAMES['iphone-14'];
  }, [device]);

  /**
   * Set platform and auto-switch device
   */
  const setPlatform = useCallback((newPlatform: MobilePlatform) => {
    setPlatformState(newPlatform);
    // Auto-switch device based on platform
    if (newPlatform === 'android' && (device.startsWith('iphone') || !DEVICE_FRAMES[device])) {
      setDevice('pixel-7');
    } else if (newPlatform === 'ios' && !device.startsWith('iphone')) {
      setDevice('iphone-14');
    }
  }, [device]);

  /**
   * Add component to canvas
   */
  const addComponent = useCallback((type: MobileComponentType, customProps?: Record<string, unknown>) => {
    const defaults = COMPONENT_DEFAULTS[type];
    if (!defaults) return;

    const newComponent: MobileComponent = {
      id: `${type}-${Date.now()}`,
      type,
      label: type.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
      props: { ...defaults.props, ...customProps },
      x: 20,
      y: 100 + components.length * 80,
      width: defaults.size.width,
      height: defaults.size.height,
    };

    setComponents((prev) => [...prev, newComponent]);
    setSelectedComponent(newComponent.id);
  }, [components.length]);

  /**
   * Update component
   */
  const updateComponent = useCallback((id: string, updates: Partial<MobileComponent>) => {
    setComponents((prev) =>
      prev.map((comp) => (comp.id === id ? { ...comp, ...updates } : comp))
    );
  }, []);

  /**
   * Delete component
   */
  const deleteComponent = useCallback((id: string) => {
    setComponents((prev) => prev.filter((c) => c.id !== id));
    if (selectedComponent === id) {
      setSelectedComponent(null);
    }
  }, [selectedComponent]);

  /**
   * Select component
   */
  const selectComponent = useCallback((id: string | null) => {
    setSelectedComponent(id);
  }, []);

  /**
   * Clear all components
   */
  const clearComponents = useCallback(() => {
    setComponents([]);
    setSelectedComponent(null);
  }, []);

  /**
   * Generate React Native code
   */
  const generateReactNativeCode = useCallback((): string => {
    const imports: string[] = [
      "import React, { useState } from 'react';",
      "import { View, Text, StyleSheet, Platform, Switch, Slider, FlatList, TouchableOpacity, Image, TextInput } from 'react-native';",
    ];

    const componentCode: string[] = [];
    const stateDeclarations: string[] = [];

    // Generate code for each component
    components.forEach((comp) => {
      switch (comp.type) {
        case 'navigation-bar':
          componentCode.push(`      <View style={styles.navBar}>
        <Text style={styles.navTitle}>${comp.props.title}</Text>
      </View>`);
          break;

        case 'toggle-switch':
          stateDeclarations.push(`  const [${comp.id}Value, set${comp.id}Value] = useState(${comp.props.value});`);
          componentCode.push(`      <View style={styles.switchContainer}>
        <Text style={styles.label}>${comp.props.label}</Text>
        <Switch
          value={${comp.id}Value}
          onValueChange={set${comp.id}Value}
        />
      </View>`);
          break;

        case 'slider':
          stateDeclarations.push(`  const [${comp.id}Value, set${comp.id}Value] = useState(${comp.props.value});`);
          componentCode.push(`      <View style={styles.sliderContainer}>
        <Text style={styles.label}>${comp.props.label}: {${comp.id}Value}</Text>
        <Slider
          style={styles.slider}
          minimumValue={${comp.props.min}}
          maximumValue={${comp.props.max}}
          value={${comp.id}Value}
          onValueChange={set${comp.id}Value}
        />
      </View>`);
          break;

        case 'list':
          stateDeclarations.push(`  const ${comp.id}Data = ${JSON.stringify(comp.props.items)};`);
          componentCode.push(`      <FlatList
        data={${comp.id}Data}
        renderItem={({ item }) => (
          <TouchableOpacity style={styles.listItem}>
            <Text>{item}</Text>
          </TouchableOpacity>
        )}
      />`);
          break;

        case 'button':
          componentCode.push(`      <TouchableOpacity style={styles.button}>
        <Text style={styles.buttonText}>${comp.props.title}</Text>
      </TouchableOpacity>`);
          break;

        case 'text-input':
          stateDeclarations.push(`  const [${comp.id}Value, set${comp.id}Value] = useState('${comp.props.value}');`);
          componentCode.push(`      <TextInput
        style={styles.textInput}
        placeholder="${comp.props.placeholder}"
        value={${comp.id}Value}
        onChangeText={set${comp.id}Value}
      />`);
          break;

        case 'image':
          componentCode.push(`      <Image
        source={{ uri: '${comp.props.source}' }}
        style={styles.image}
      />`);
          break;

        case 'card':
          componentCode.push(`      <View style={styles.card}>
        <Text style={styles.cardTitle}>${comp.props.title}</Text>
        <Text style={styles.cardContent}>${comp.props.content}</Text>
      </View>`);
          break;
      }
    });

    const screenName = (node?.data as { label?: string })?.label || 'MobileScreen';

    return `${imports.join('\n')}

const ${screenName} = () => {
${stateDeclarations.join('\n')}

  return (
    <View style={styles.container}>
${componentCode.join('\n')}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  navBar: {
    height: Platform.select({ ios: 44, android: 56 }),
    backgroundColor: Platform.select({ ios: '#007AFF', android: '#2196F3' }),
    justifyContent: 'center',
    alignItems: 'center',
  },
  navTitle: {
    fontSize: Platform.select({ ios: 17, android: 20 }),
    fontWeight: '600',
    color: '#fff',
  },
  switchContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 16,
  },
  sliderContainer: {
    padding: 16,
  },
  slider: {
    width: '100%',
  },
  label: {
    fontSize: 16,
  },
  listItem: {
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  button: {
    backgroundColor: Platform.select({ ios: '#007AFF', android: '#2196F3' }),
    padding: 16,
    borderRadius: 8,
    margin: 16,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
  },
  textInput: {
    height: 48,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    paddingHorizontal: 16,
    margin: 16,
  },
  image: {
    width: 150,
    height: 150,
    margin: 16,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 16,
    margin: 16,
    elevation: 2,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  cardContent: {
    fontSize: 14,
    color: '#666',
  },
});

export default ${screenName};`;
  }, [components, node]);

  /**
   * Export code to file
   */
  const exportToFile = useCallback((code: string, filename: string) => {
    const blob = new Blob([code], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename.endsWith('.tsx') ? filename : `${filename}.tsx`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, []);

  /**
   * Get component count
   */
  const getComponentCount = useCallback((type?: MobileComponentType): number => {
    if (type) {
      return components.filter((c) => c.type === type).length;
    }
    return components.length;
  }, [components]);

  /**
   * Get component by ID
   */
  const getComponentById = useCallback((id: string): MobileComponent | undefined => {
    return components.find((c) => c.id === id);
  }, [components]);

  return {
    // State
    platform,
    device,
    components,
    selectedComponent,
    deviceFrame,

    // Actions
    setPlatform,
    setDevice,
    addComponent,
    updateComponent,
    deleteComponent,
    selectComponent,
    clearComponents,

    // Code Generation
    generateReactNativeCode,
    exportToFile,

    // Utilities
    getComponentCount,
    getComponentById,
  };
}
