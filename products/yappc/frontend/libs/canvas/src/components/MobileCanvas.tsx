/**
 * MobileCanvas Component
 * 
 * Mobile app builder with device frames, mobile components, and React Native export.
 * Implements Journey 9.1 from YAPPC_USER_JOURNEYS.md (Mobile Engineer - Mobile Screen Design).
 * 
 * Features:
 * - Device frame preview (iPhone/Android)
 * - Mobile-specific components (Switch, Slider, List, NavigationBar)
 * - iOS/Android split view
 * - React Native code export
 * - Platform-specific styling
 * 
 * @doc.type component
 * @doc.purpose Mobile app screen design and code generation
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Box, Surface as Paper, Typography, ToggleButton, ToggleButtonGroup, Button, IconButton, InteractiveList as List, ListItem, ListItemButton, ListItemText, ListItemIcon, Dialog, DialogTitle, DialogContent, DialogActions, Divider, Collapse, Alert } from '@ghatana/ui';
import { Smartphone as AndroidIcon, TabletSmartphone as IosIcon, Columns2 as SplitViewIcon, X as CloseIcon, Download as DownloadIcon, Copy as CopyIcon, Plus as AddIcon, ChevronDown as ExpandMoreIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

/**
 * Mobile platform type
 */
export type MobilePlatform = 'ios' | 'android' | 'both';

/**
 * Mobile component types available in the palette
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
interface DeviceFrame {
    name: string;
    width: number;
    height: number;
    statusBarHeight: number;
    notchHeight?: number;
}

/**
 * Component properties
 */
interface MobileCanvasProps {
    /** Node context for integration */
    node?: Node;
    /** Callback when code is generated */
    onCodeGenerated?: (code: string) => void;
    /** Initial platform */
    initialPlatform?: MobilePlatform;
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

// ============================================================================
// COMPONENT LIBRARY
// ============================================================================

const MOBILE_COMPONENTS: Array<{ type: MobileComponentType; label: string; defaultProps: Record<string, unknown>; defaultSize: { width: number; height: number } }> = [
    {
        type: 'navigation-bar',
        label: 'Navigation Bar',
        defaultProps: { title: 'Screen Title' },
        defaultSize: { width: 360, height: 64 },
    },
    {
        type: 'toggle-switch',
        label: 'Toggle Switch',
        defaultProps: { label: 'Enable Notifications', value: false },
        defaultSize: { width: 300, height: 48 },
    },
    {
        type: 'slider',
        label: 'Slider',
        defaultProps: { label: 'Frequency', min: 0, max: 100, value: 50 },
        defaultSize: { width: 300, height: 64 },
    },
    {
        type: 'list',
        label: 'List',
        defaultProps: { items: ['Item 1', 'Item 2', 'Item 3'] },
        defaultSize: { width: 360, height: 200 },
    },
    {
        type: 'button',
        label: 'Button',
        defaultProps: { title: 'Submit', variant: 'contained' },
        defaultSize: { width: 200, height: 48 },
    },
    {
        type: 'text-input',
        label: 'Text Input',
        defaultProps: { placeholder: 'Enter text...', value: '' },
        defaultSize: { width: 300, height: 48 },
    },
    {
        type: 'image',
        label: 'Image',
        defaultProps: { source: 'https://via.placeholder.com/150', alt: 'Placeholder' },
        defaultSize: { width: 150, height: 150 },
    },
    {
        type: 'card',
        label: 'Card',
        defaultProps: { title: 'Card Title', content: 'Card content' },
        defaultSize: { width: 340, height: 120 },
    },
];

// ============================================================================
// COMPONENT
// ============================================================================

/**
 * Mobile canvas for designing mobile app screens
 */
export const MobileCanvas: React.FC<MobileCanvasProps> = ({
    node,
    onCodeGenerated,
    initialPlatform = 'ios',
}) => {
    // State
    const [platform, setPlatform] = useState<MobilePlatform>(initialPlatform);
    const [device, setDevice] = useState<string>('iphone-14');
    const [components, setComponents] = useState<MobileComponent[]>([]);
    const [selectedComponent, setSelectedComponent] = useState<string | null>(null);
    const [libraryExpanded, setLibraryExpanded] = useState(true);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [generatedCode, setGeneratedCode] = useState<string>('');
    const [copied, setCopied] = useState(false);

    // Get device frame
    const deviceFrame = useMemo(() => {
        if (platform === 'android' && (device.startsWith('iphone') || device === 'iphone-14-pro')) {
            return DEVICE_FRAMES['pixel-7'];
        }
        return DEVICE_FRAMES[device] || DEVICE_FRAMES['iphone-14'];
    }, [platform, device]);

    /**
     * Handle platform change
     */
    const handlePlatformChange = useCallback((
        _event: React.MouseEvent<HTMLElement>,
        newPlatform: MobilePlatform | null
    ) => {
        if (newPlatform) {
            setPlatform(newPlatform);
            // Auto-switch device based on platform
            if (newPlatform === 'android') {
                setDevice('pixel-7');
            } else if (newPlatform === 'ios') {
                setDevice('iphone-14');
            }
        }
    }, []);

    /**
     * Add component to canvas
     */
    const addComponent = useCallback((componentType: MobileComponentType) => {
        const componentDef = MOBILE_COMPONENTS.find((c) => c.type === componentType);
        if (!componentDef) return;

        const newComponent: MobileComponent = {
            id: `${componentType}-${Date.now()}`,
            type: componentType,
            label: componentDef.label,
            props: { ...componentDef.defaultProps },
            x: 20,
            y: 100 + components.length * 80, // Stack components vertically
            width: componentDef.defaultSize.width,
            height: componentDef.defaultSize.height,
        };

        setComponents((prev) => [...prev, newComponent]);
        setSelectedComponent(newComponent.id);
    }, [components.length]);

    /**
     * Delete component
     */
    const deleteComponent = useCallback((componentId: string) => {
        setComponents((prev) => prev.filter((c) => c.id !== componentId));
        if (selectedComponent === componentId) {
            setSelectedComponent(null);
        }
    }, [selectedComponent]);

    /**
     * Generate React Native code
     */
    const generateReactNativeCode = useCallback(() => {
        const imports: string[] = [
            "import React, { useState } from 'react';",
            "import { View, Text, StyleSheet, Platform, Switch, Slider, FlatList, TouchableOpacity, Image, TextInput } from 'react-native';",
        ];

        const componentCode: string[] = [];
        const stateDeclarations: string[] = [];

        // Generate state and component code for each component
        components.forEach((comp) => {
            switch (comp.type) {
                case 'navigation-bar':
                    componentCode.push(`
  {/* Navigation Bar */}
  <View style={styles.navBar}>
    <Text style={styles.navTitle}>${comp.props.title}</Text>
  </View>`);
                    break;

                case 'toggle-switch':
                    stateDeclarations.push(`  const [${comp.id}Value, set${comp.id}Value] = useState(${comp.props.value});`);
                    componentCode.push(`
  {/* Toggle Switch */}
  <View style={styles.switchContainer}>
    <Text style={styles.label}>${comp.props.label}</Text>
    <Switch
      value={${comp.id}Value}
      onValueChange={set${comp.id}Value}
      trackColor={{ false: '#767577', true: Platform.OS === 'ios' ? '#81b0ff' : '#4CAF50' }}
      thumbColor={${comp.id}Value ? (Platform.OS === 'ios' ? '#f4f3f4' : '#ffffff') : '#f4f3f4'}
    />
  </View>`);
                    break;

                case 'slider':
                    stateDeclarations.push(`  const [${comp.id}Value, set${comp.id}Value] = useState(${comp.props.value});`);
                    componentCode.push(`
  {/* Slider */}
  <View style={styles.sliderContainer}>
    <Text style={styles.label}>${comp.props.label}: {${comp.id}Value}</Text>
    <Slider
      style={styles.slider}
      minimumValue={${comp.props.min}}
      maximumValue={${comp.props.max}}
      value={${comp.id}Value}
      onValueChange={set${comp.id}Value}
      minimumTrackTintColor={Platform.OS === 'ios' ? '#007AFF' : '#2196F3'}
      maximumTrackTintColor="#d3d3d3"
      thumbTintColor={Platform.OS === 'ios' ? '#007AFF' : '#2196F3'}
    />
  </View>`);
                    break;

                case 'list':
                    stateDeclarations.push(`  const ${comp.id}Data = ${JSON.stringify(comp.props.items)};`);
                    componentCode.push(`
  {/* List */}
  <FlatList
    data={${comp.id}Data}
    keyExtractor={(item, index) => index.toString()}
    renderItem={({ item }) => (
      <TouchableOpacity style={styles.listItem}>
        <Text style={styles.listItemText}>{item}</Text>
      </TouchableOpacity>
    )}
  />`);
                    break;

                case 'button':
                    componentCode.push(`
  {/* Button */}
  <TouchableOpacity style={styles.button}>
    <Text style={styles.buttonText}>${comp.props.title}</Text>
  </TouchableOpacity>`);
                    break;

                case 'text-input':
                    stateDeclarations.push(`  const [${comp.id}Value, set${comp.id}Value] = useState('${comp.props.value}');`);
                    componentCode.push(`
  {/* Text Input */}
  <TextInput
    style={styles.textInput}
    placeholder="${comp.props.placeholder}"
    value={${comp.id}Value}
    onChangeText={set${comp.id}Value}
  />`);
                    break;

                case 'image':
                    componentCode.push(`
  {/* Image */}
  <Image
    source={{ uri: '${comp.props.source}' }}
    style={styles.image}
    resizeMode="cover"
  />`);
                    break;

                case 'card':
                    componentCode.push(`
  {/* Card */}
  <View style={styles.card}>
    <Text style={styles.cardTitle}>${comp.props.title}</Text>
    <Text style={styles.cardContent}>${comp.props.content}</Text>
  </View>`);
                    break;
            }
        });

        const screenName = node?.data?.label || 'MobileScreen';
        const code = `${imports.join('\n')}

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
    paddingTop: Platform.OS === 'ios' ? 50 : 30,
  },
  navBar: {
    height: Platform.OS === 'ios' ? 44 : 56,
    backgroundColor: Platform.OS === 'ios' ? '#007AFF' : '#2196F3',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  navTitle: {
    fontSize: Platform.OS === 'ios' ? 17 : 20,
    fontWeight: Platform.OS === 'ios' ? '600' : '500',
    color: '#fff',
  },
  switchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  sliderContainer: {
    padding: 16,
    backgroundColor: '#fff',
  },
  slider: {
    width: '100%',
    marginTop: 8,
  },
  label: {
    fontSize: 16,
    color: '#333',
  },
  listItem: {
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  listItemText: {
    fontSize: 16,
    color: '#333',
  },
  button: {
    backgroundColor: Platform.OS === 'ios' ? '#007AFF' : '#2196F3',
    padding: 16,
    borderRadius: Platform.OS === 'ios' ? 10 : 4,
    alignItems: 'center',
    margin: 16,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: Platform.OS === 'ios' ? '600' : '500',
  },
  textInput: {
    height: 48,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: Platform.OS === 'ios' ? 10 : 4,
    paddingHorizontal: 16,
    margin: 16,
    fontSize: 16,
  },
  image: {
    width: 150,
    height: 150,
    margin: 16,
    borderRadius: 8,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 16,
    margin: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
  },
  cardContent: {
    fontSize: 14,
    color: '#666',
  },
});

export default ${screenName};`;

        setGeneratedCode(code);
        setExportDialogOpen(true);
        onCodeGenerated?.(code);
    }, [components, node, onCodeGenerated]);

    /**
     * Copy code to clipboard
     */
    const copyToClipboard = useCallback(async () => {
        try {
            await navigator.clipboard.writeText(generatedCode);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch (error) {
            console.error('Failed to copy:', error);
        }
    }, [generatedCode]);

    /**
     * Download code as file
     */
    const downloadCode = useCallback(() => {
        const screenName = node?.data?.label || 'MobileScreen';
        const blob = new Blob([generatedCode], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${screenName}.tsx`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }, [generatedCode, node]);

    // Render
    return (
        <Box className="flex h-screen bg-gray-50 dark:bg-gray-950">
            {/* Component Library Sidebar */}
            <Paper
                elevation={3}
                className="transition-all duration-300 flex flex-col rounded-none" style={{ width: libraryExpanded ? 280 : 48 }}
            >
                {/* Header */}
                <Box className="p-4 flex items-center justify-between">
                    {libraryExpanded && <Typography as="h6">Components</Typography>}
                    <IconButton size="sm" onClick={() => setLibraryExpanded(!libraryExpanded)}>
                        <ExpandMoreIcon style={{ transform: libraryExpanded ? 'rotate(180deg)' : 'rotate(90deg)' }} />
                    </IconButton>
                </Box>

                {/* Component List */}
                <Collapse in={libraryExpanded}>
                    <Divider />
                    <List dense>
                        {MOBILE_COMPONENTS.map((comp) => (
                            <ListItem key={comp.type} disablePadding>
                                <ListItemButton onClick={() => addComponent(comp.type)}>
                                    <ListItemIcon>
                                        <AddIcon size={16} />
                                    </ListItemIcon>
                                    <ListItemText primary={comp.label} />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                </Collapse>
            </Paper>

            {/* Main Canvas Area */}
            <Box className="flex-1 flex flex-col p-4">
                {/* Toolbar */}
                <Paper variant="raised" className="p-4 mb-4 flex items-center gap-4">
                    {/* Platform Selector */}
                    <ToggleButtonGroup
                        value={platform}
                        exclusive
                        onChange={handlePlatformChange}
                        size="sm"
                    >
                        <ToggleButton value="ios">
                            <IosIcon className="mr-2" />
                            iOS
                        </ToggleButton>
                        <ToggleButton value="android">
                            <AndroidIcon className="mr-2" />
                            Android
                        </ToggleButton>
                        <ToggleButton value="both">
                            <SplitViewIcon className="mr-2" />
                            Both
                        </ToggleButton>
                    </ToggleButtonGroup>

                    <Divider orientation="vertical" flexItem />

                    {/* Device Selector */}
                    <Typography as="p" className="text-sm" color="text.secondary">
                        {deviceFrame.name}
                    </Typography>

                    <Box className="flex-1" />

                    {/* Export Button */}
                    <Button
                        variant="solid"
                        startIcon={<DownloadIcon />}
                        onClick={generateReactNativeCode}
                        disabled={components.length === 0}
                    >
                        Export to React Native
                    </Button>
                </Paper>

                {/* Device Frame(s) */}
                <Box
                    className="flex-1 flex justify-center items-center gap-8 overflow-auto"
                >
                    {/* iOS Frame */}
                    {(platform === 'ios' || platform === 'both') && (
                        <Paper
                            elevation={8}
                            className="overflow-hidden relative rounded-[48px] bg-[#fff] w-[deviceFrame.width] h-[deviceFrame.height]" >
                            {/* Status Bar */}
                            <Box
                                className="flex items-center justify-center bg-[#f5f5f5] border-b border-solid border-b-[#e0e0e0] h-[deviceFrame.statusBarHeight]" >
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    iOS - {deviceFrame.name}
                                </Typography>
                            </Box>

                            {/* Screen Content */}
                            <Box className="overflow-auto bg-[#fafafa] h-[deviceFrame.height - deviceFrame.statusBarHeight]" >
                                {components.map((comp) => (
                                    <Box
                                        key={comp.id}
                                        onClick={() => setSelectedComponent(comp.id)}
                                        className="relative p-2 border-b border-solid border-b-[#e0e0e0] cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800" style={{ backgroundColor: selectedComponent === comp.id ? 'rgba(0,0,0,0.08)' : 'transparent' }}
                                    >
                                        <Typography as="p" className="text-sm">{comp.label}</Typography>
                                        {selectedComponent === comp.id && (
                                            <IconButton
                                                size="sm"
                                                className="absolute top-[4px] right-[4px]"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    deleteComponent(comp.id);
                                                }}
                                            >
                                                <CloseIcon size={16} />
                                            </IconButton>
                                        )}
                                    </Box>
                                ))}
                            </Box>
                        </Paper>
                    )}

                    {/* Android Frame */}
                    {(platform === 'android' || platform === 'both') && (
                        <Paper
                            elevation={8}
                            className="overflow-hidden relative rounded-[24px] bg-[#fff]" style={{ width: DEVICE_FRAMES['pixel-7'].width, height: DEVICE_FRAMES['pixel-7'].height }}
                        >
                            {/* Status Bar */}
                            <Box
                                className="flex items-center justify-center bg-[#212121]" style={{ height: DEVICE_FRAMES['pixel-7'].statusBarHeight }}>
                                <Typography as="span" className="text-xs text-gray-500" color="white">
                                    Android - {DEVICE_FRAMES['pixel-7'].name}
                                </Typography>
                            </Box>

                            {/* Screen Content */}
                            <Box className="overflow-auto bg-[#fafafa]" style={{ height: DEVICE_FRAMES['pixel-7'].height - DEVICE_FRAMES['pixel-7'].statusBarHeight }} >
                                {components.map((comp) => (
                                    <Box
                                        key={comp.id}
                                        onClick={() => setSelectedComponent(comp.id)}
                                        className="relative p-2 border-b border-solid border-b-[#e0e0e0] cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800" style={{ backgroundColor: selectedComponent === comp.id ? 'rgba(0,0,0,0.08)' : 'transparent' }}
                                    >
                                        <Typography as="p" className="text-sm">{comp.label}</Typography>
                                        {selectedComponent === comp.id && (
                                            <IconButton
                                                size="sm"
                                                className="absolute top-[4px] right-[4px]"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    deleteComponent(comp.id);
                                                }}
                                            >
                                                <CloseIcon size={16} />
                                            </IconButton>
                                        )}
                                    </Box>
                                ))}
                            </Box>
                        </Paper>
                    )}
                </Box>

                {/* No Components Message */}
                {components.length === 0 && (
                    <Box
                        className="absolute text-center top-[50%] left-[50%]" >
                        <Typography as="h6" color="text.secondary" gutterBottom>
                            No Components Added
                        </Typography>
                        <Typography as="p" className="text-sm" color="text.secondary">
                            Click on components in the sidebar to add them to your mobile screen
                        </Typography>
                    </Box>
                )}
            </Box>

            {/* Export Dialog */}
            <Dialog
                open={exportDialogOpen}
                onClose={() => setExportDialogOpen(false)}
                size="md"
                fullWidth
            >
                <DialogTitle>
                    Exported React Native Code
                    <IconButton
                        onClick={() => setExportDialogOpen(false)}
                        className="absolute right-[8px] top-[8px]"
                    >
                        <CloseIcon />
                    </IconButton>
                </DialogTitle>
                <DialogContent dividers>
                    <Alert severity="success" className="mb-4">
                        Component exported. Compatible with Expo.
                    </Alert>
                    <Paper
                        variant="flat"
                        className="p-4 overflow-auto whitespace-pre-wrap break-words bg-[#f5f5f5] max-h-[500px] font-mono text-xs"
                    >
                        {generatedCode}
                    </Paper>
                </DialogContent>
                <DialogActions>
                    <Button onClick={copyToClipboard} startIcon={<CopyIcon />}>
                        {copied ? 'Copied!' : 'Copy to Clipboard'}
                    </Button>
                    <Button onClick={downloadCode} startIcon={<DownloadIcon />} variant="solid">
                        Download File
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
