/**
 * Unified Canvas - Complete Integration Example
 *
 * Demonstrates how all canvas components work together.
 *
 * @doc.type example
 * @doc.purpose Integration demo
 * @doc.layer product
 * @doc.pattern Application
 */

import React from 'react';
import { Provider } from 'jotai';
import { CanvasChromeLayout } from './CanvasChromeLayout';
import { Canvas } from './Canvas';
import { Frame } from './Frame';
import { OutlinePanel } from './OutlinePanel';
import { ContextBar } from './ContextBar';
import { ZoomHUD } from './ZoomHUD';
import { MinimapPanel } from './MinimapPanel';
import { InspectorPanel } from '../shell/InspectorPanel';
import { PalettePanel } from '../shell/PalettePanel';
import { ContrastDebugOverlay } from './ContrastDebugOverlay';

/**
 * Unified Canvas Application
 *
 * Complete canvas with all features enabled.
 */
export const UnifiedCanvasApp: React.FC = () => {
  return (
    <Provider>
      {/* Dev Mode Contrast Debugging */}
      <ContrastDebugOverlay enabled={process.env.NODE_ENV === 'development'} />

      {/* Master Chrome Layout */}
      <CanvasChromeLayout
        defaultCalmMode={true}
        /* Left Rail */
        leftRail={
          <div
            style={{ display: 'flex', flexDirection: 'column', height: '100%' }}
          >
            <PalettePanel />
          </div>
        }
        /* Outline Panel */
        outline={
          <OutlinePanel
            onItemSelect={(id, type) => {
              console.log('Selected:', type, id);
            }}
            onItemFocus={(id, type) => {
              console.log('Focus:', type, id);
            }}
          />
        }
        /* Inspector Panel */
        inspector={<InspectorPanel />}
        /* Minimap */
        minimap={<MinimapPanel />}
        /* Context Bar */
        contextBar={
          <ContextBar
            onAction={(actionId) => {
              console.log('Action:', actionId);
            }}
          />
        }
      >
        {/* Main Canvas Content */}
        <Canvas>
          {/* Example Frames */}
          <Frame
            id="frame-1"
            phase="discover"
            label="Discovery Phase"
            x={100}
            y={100}
            width={400}
            height={300}
          >
            {/* Frame content (artifacts) would go here */}
            <div style={{ padding: '20px', color: '#666' }}>
              Artifacts will be rendered here...
            </div>
          </Frame>

          <Frame
            id="frame-2"
            phase="design"
            label="Design Phase"
            x={600}
            y={100}
            width={400}
            height={300}
          >
            <div style={{ padding: '20px', color: '#666' }}>
              Design artifacts...
            </div>
          </Frame>

          <Frame
            id="frame-3"
            phase="build"
            label="Build Phase"
            x={1100}
            y={100}
            width={400}
            height={300}
          >
            <div style={{ padding: '20px', color: '#666' }}>
              Build artifacts...
            </div>
          </Frame>
        </Canvas>

        {/* Zoom HUD (overlaid on canvas) */}
        <ZoomHUD
          onZoomChange={(zoom) => {
            console.log('Zoom changed:', zoom);
          }}
          onFitToView={() => {
            console.log('Fit to view');
          }}
        />
      </CanvasChromeLayout>

      {/* Keyboard Shortcuts Guide */}
      <div
        style={{
          position: 'fixed',
          bottom: '16px',
          right: '16px',
          padding: '12px',
          background: 'rgba(0,0,0,0.8)',
          color: 'white',
          borderRadius: '8px',
          fontSize: '11px',
          fontFamily: 'monospace',
          zIndex: 999999,
          maxWidth: '300px',
        }}
      >
        <div style={{ fontWeight: 'bold', marginBottom: '8px' }}>
          Keyboard Shortcuts
        </div>
        <div>⌘+Shift+L - Toggle Left Rail</div>
        <div>⌘+Shift+I - Toggle Inspector</div>
        <div>⌘+Shift+O - Toggle Outline</div>
        <div>⌘+Shift+M - Toggle Minimap</div>
        <div>⌘+Shift+C - Toggle Calm Mode</div>
        <div>Escape - Hide Context Bar</div>
        <div>Ctrl+- - Zoom Out</div>
        <div>Ctrl++ - Zoom In</div>
        <div>Ctrl+0 - Reset Zoom</div>
        <div>Ctrl+1 - Fit to View</div>
      </div>
    </Provider>
  );
};

export default UnifiedCanvasApp;
