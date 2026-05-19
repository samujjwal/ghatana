/**
 * @fileoverview Design System Generation Page
 *
 * Provides a workflow for generating design systems from tokens,
 * with options for different output formats and customization.
 *
 * @doc.type component
 * @doc.purpose DS generation workflow UI
 * @doc.layer studio
 */

import { useState } from 'react';
import {
  renderPresetToCss,
  materializePreset,
  type DesignSystemPreset,
} from '@ghatana/ds-generator';
import { PRESET_GHATANA_DEFAULT, PRESET_ENTERPRISE_NEUTRAL, PRESET_CREATIVE_BOLD } from '@ghatana/ds-generator';

export function DesignSystemPage() {
  const [selectedPreset, setSelectedPreset] = useState<DesignSystemPreset>(PRESET_GHATANA_DEFAULT);
  const [outputFormat, setOutputFormat] = useState<'css' | 'json' | 'tailwind'>('css');
  const [generatedOutput, setGeneratedOutput] = useState<string>('');
  const [isGenerating, setIsGenerating] = useState(false);

  const presets = [
    { name: 'Ghatana Default', preset: PRESET_GHATANA_DEFAULT },
    { name: 'Enterprise Neutral', preset: PRESET_ENTERPRISE_NEUTRAL },
    { name: 'Creative Bold', preset: PRESET_CREATIVE_BOLD },
  ];

  const handleGenerate = async () => {
    setIsGenerating(true);
    
    // Simulate async generation
    await new Promise(resolve => setTimeout(resolve, 500));

    let output = '';
    switch (outputFormat) {
      case 'css':
        output = renderPresetToCss(selectedPreset);
        break;
      case 'json':
        output = JSON.stringify(selectedPreset, null, 2);
        break;
      case 'tailwind':
        output = JSON.stringify(
          {
            extend: {
              colors: selectedPreset.colors,
              spacing: materializePreset(selectedPreset).spacing,
              borderRadius: selectedPreset.borderRadius,
            },
          },
          null,
          2,
        );
        break;
    }

    setGeneratedOutput(output);
    setIsGenerating(false);
  };

  const handleDownload = () => {
    const blob = new Blob([generatedOutput], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `design-system.${outputFormat === 'json' ? 'json' : 'css'}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">Design System Generator</h1>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Configuration Panel */}
        <div className="space-y-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">Configuration</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">Preset</label>
                <select
                  className="w-full border rounded px-3 py-2"
                  value={presets.findIndex(p => p.preset === selectedPreset)}
                  onChange={(e) => setSelectedPreset(presets[Number(e.target.value)].preset)}
                >
                  {presets.map((preset, index) => (
                    <option key={index} value={index}>
                      {preset.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">Output Format</label>
                <div className="flex gap-2">
                  {(['css', 'json', 'tailwind'] as const).map((format) => (
                    <button
                      key={format}
                      onClick={() => setOutputFormat(format)}
                      className={`px-4 py-2 rounded ${
                        outputFormat === format
                          ? 'bg-blue-500 text-white'
                          : 'bg-gray-100 hover:bg-gray-200'
                      }`}
                    >
                      {format.toUpperCase()}
                    </button>
                  ))}
                </div>
              </div>

              <button
                onClick={handleGenerate}
                disabled={isGenerating}
                className="w-full bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600 disabled:opacity-50"
              >
                {isGenerating ? 'Generating...' : 'Generate Design System'}
              </button>
            </div>
          </div>

          {/* Preview */}
          {generatedOutput && (
            <div className="bg-white rounded-lg shadow p-6">
              <h2 className="text-lg font-semibold mb-4">Preview</h2>
              <pre className="bg-gray-50 p-4 rounded overflow-auto max-h-96 text-sm">
                {generatedOutput}
              </pre>
              <button
                onClick={handleDownload}
                className="mt-4 bg-green-500 text-white px-4 py-2 rounded hover:bg-green-600"
              >
                Download
              </button>
            </div>
          )}
        </div>

        {/* Info Panel */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-semibold mb-4">Design System Tokens</h2>
          <div className="space-y-4">
            <div>
              <h3 className="font-medium mb-2">Colors</h3>
              <div className="grid grid-cols-4 gap-2">
                {Object.entries(selectedPreset.colors).slice(0, 8).map(([key, value]) => (
                  <div key={key} className="text-center">
                    <div
                      className="w-full h-12 rounded border"
                      style={{ backgroundColor: value as string }}
                    />
                    <span className="text-xs mt-1 block">{key}</span>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h3 className="font-medium mb-2">Spacing</h3>
              <div className="flex gap-2 flex-wrap">
                {Object.entries(materializePreset(selectedPreset).spacing).slice(0, 6).map(([key, value]) => (
                  <div key={key} className="text-sm bg-gray-100 px-2 py-1 rounded">
                    {key}: {value}
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h3 className="font-medium mb-2">Typography</h3>
              <div className="text-sm">
                <p>Font: {selectedPreset.typography.fontFamily}</p>
                <p>Base Size: {selectedPreset.typography.baseFontSize}px</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
