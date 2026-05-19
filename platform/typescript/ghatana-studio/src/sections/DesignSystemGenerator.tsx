/**
 * @fileoverview Design System Generator Studio Section
 *
 * Provides a UI for generating design system tokens from presets and brands,
 * with support for multiple output formats (CSS, Tailwind, React theme, JSON).
 *
 * @doc.type component
 * @doc.purpose Design system generation workflow in Studio
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState } from 'react';
import { Button, Typography, Card, CardContent, CardHeader } from '@ghatana/design-system';

import {
  materializePreset,
  findPreset,
  ALL_PRESETS,
} from '@ghatana/ds-generator';
import {
  generateCSSVariables,
  generateTailwindConfig,
  generateReactTheme,
  generateJSONTokens,
} from '@ghatana/ds-generator/adapters';

export default function DesignSystemGenerator(): ReactElement {
  const [selectedPresetId, setSelectedPresetId] = useState<string>('ghatana-default');
  const [selectedOutputFormat, setSelectedOutputFormat] = useState<'css' | 'tailwind' | 'react' | 'json'>('css');
  const [generatedOutput, setGeneratedOutput] = useState<string>('');

  const selectedPreset = findPreset(selectedPresetId);
  const tokens = selectedPreset ? materializePreset(selectedPreset) : null;

  const handleGenerate = (): void => {
    if (!tokens) return;

    switch (selectedOutputFormat) {
      case 'css':
        setGeneratedOutput(generateCSSVariables(tokens));
        break;
      case 'tailwind':
        setGeneratedOutput(generateTailwindConfig(tokens));
        break;
      case 'react':
        setGeneratedOutput(generateReactTheme(tokens));
        break;
      case 'json':
        setGeneratedOutput(generateJSONTokens(tokens));
        break;
    }
  };

  const handleDownload = (): void => {
    if (!generatedOutput) return;

    const extensions = {
      css: 'css',
      tailwind: 'js',
      react: 'ts',
      json: 'json',
    };

    const blob = new Blob([generatedOutput], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `design-system.${extensions[selectedOutputFormat]}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="p-6">
      <div className="mb-6">
        <Typography variant="h2" className="text-2xl font-bold mb-2">
          Design System Generator
        </Typography>
        <Typography variant="body1" className="text-gray-600">
          Generate design system tokens from presets and export to various formats.
        </Typography>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Configuration Panel */}
        <Card>
          <CardHeader title="Configuration" />
          <CardContent>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">Select Preset</label>
                <select
                  value={selectedPresetId}
                  onChange={(e) => setSelectedPresetId(e.target.value)}
                  className="w-full border rounded-md px-3 py-2"
                >
                  {ALL_PRESETS.map((preset) => (
                    <option key={preset.id} value={preset.id}>
                      {preset.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">Output Format</label>
                <select
                  value={selectedOutputFormat}
                  onChange={(e) => setSelectedOutputFormat(e.target.value as 'css' | 'tailwind' | 'react' | 'json')}
                  className="w-full border rounded-md px-3 py-2"
                >
                  <option value="css">CSS Variables</option>
                  <option value="tailwind">Tailwind Config</option>
                  <option value="react">React Theme</option>
                  <option value="json">JSON Tokens</option>
                </select>
              </div>

              <Button variant="primary" onClick={handleGenerate} className="w-full">
                Generate Design System
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Output Panel */}
        <Card>
          <CardHeader title="Generated Output" />
          <CardContent>
            {selectedPreset && (
              <div className="mb-4 p-4 bg-gray-50 rounded-md">
                <Typography variant="body2" className="font-medium mb-2">
                  {selectedPreset.name}
                </Typography>
                <Typography variant="body2" className="text-gray-600 text-sm">
                  {selectedPreset.description}
                </Typography>
              </div>
            )}

            {generatedOutput ? (
              <div className="space-y-4">
                <div className="bg-gray-900 text-gray-100 p-4 rounded-md overflow-auto max-h-96">
                  <pre className="text-sm">{generatedOutput}</pre>
                </div>
                <Button variant="secondary" onClick={handleDownload} className="w-full">
                  Download {selectedOutputFormat.toUpperCase()}
                </Button>
              </div>
            ) : (
              <Typography variant="body1" className="text-gray-500 text-center py-8">
                Click "Generate Design System" to create output
              </Typography>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
