import { useState } from 'react';
import type { ChangeEvent } from 'react';

interface ConfigUploaderProps {
  onConfigLoaded: (config: Record<string, unknown>) => void;
}

export function ConfigUploader({ onConfigLoaded }: ConfigUploaderProps) {
  const [error, setError] = useState('');

  const handleFileUpload = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const config = JSON.parse(e.target?.result as string) as Record<string, unknown>;
        onConfigLoaded(config);
        // Save to chrome storage
        void chrome.storage.local.set({ config });
      } catch (err) {
        // Mark `err` as intentionally unused for linters.
        void err;
        setError('Invalid configuration file format');
      }
    };
    reader.onerror = () => setError('Error reading file');
    reader.readAsText(file);
  };

  return (
    <div className="config-uploader">
      <h3>Load Configuration</h3>
      <input
        type="file"
        accept=".json"
        onChange={handleFileUpload}
        style={{ display: 'none' }}
        id="config-file-input"
      />
      <button
        onClick={() => document.getElementById('config-file-input')?.click()}
        className="btn btn-primary"
      >
        Select Config File
      </button>
      {error && <div className="error-message">{error}</div>}
    </div>
  );
}
