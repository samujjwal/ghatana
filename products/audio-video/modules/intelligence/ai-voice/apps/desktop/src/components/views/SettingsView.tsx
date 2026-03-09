/**
 * AI Voice Production Studio - Settings View
 * 
 * @doc.type component
 * @doc.purpose Application settings
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useEffect, useCallback } from 'react';
import type { ModelDownloadInfo } from '../../types';
import { createLogger, invokeWithLog } from '../../utils/logger';

const logger = createLogger('SettingsView');

export const SettingsView: React.FC = () => {
  const [models, setModels] = useState<ModelDownloadInfo[]>([]);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadModels();
  }, []);

  const loadModels = async () => {
    try {
      const available = await invokeWithLog<ModelDownloadInfo[]>(logger, 'ai_voice_list_available_models');
      setModels(available);
    } catch (err) {
      logger.error('LoadModels:error', {}, err);
      setError(err instanceof Error ? err.message : 'Failed to load models');
    }
  };

  const handleDownload = useCallback(async (model: ModelDownloadInfo) => {
    setDownloading(model.id);
    try {
      logger.info('DownloadModel:request', { model_id: model.id });
      await invokeWithLog<void>(logger, 'ai_voice_download_model', { model_id: model.id });
      await loadModels();
    } catch (err) {
      logger.error('DownloadModel:error', { model_id: model.id }, err);
      setError(err instanceof Error ? err.message : 'Download failed');
    } finally {
      setDownloading(null);
    }
  }, []);

  return (
    <div className="flex-1 overflow-auto p-6">
      <div className="max-w-3xl mx-auto">
        <h2 className="text-2xl font-bold text-white mb-6">Settings</h2>

        {error && (
          <div className="bg-red-600/20 border border-red-600 rounded-lg p-4 mb-6">
            <p className="text-red-400">{error}</p>
          </div>
        )}

        {/* Model Downloads */}
        <section className="bg-gray-800 rounded-lg p-6 mb-6">
          <h3 className="text-lg font-medium text-white mb-4">AI Models</h3>
          <p className="text-sm text-gray-400 mb-4">
            Download required models for stem separation and voice conversion.
          </p>

          <div className="space-y-3">
            {models.map((model) => (
              <div
                key={model.id}
                className="flex items-center justify-between bg-gray-700 rounded-lg p-4"
              >
                <div>
                  <h4 className="font-medium text-white">{model.name}</h4>
                  <p className="text-sm text-gray-400">{model.description}</p>
                  <p className="text-xs text-gray-500 mt-1">
                    {(model.size / 1024 / 1024).toFixed(0)} MB • {model.type}
                  </p>
                </div>
                {model.isDownloaded ? (
                  <span className="px-3 py-1.5 text-sm rounded-lg bg-green-600/20 text-green-400">
                    Installed
                  </span>
                ) : (
                  <button
                    onClick={() => handleDownload(model)}
                    disabled={downloading === model.id}
                    className="px-3 py-1.5 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 transition-all"
                  >
                    {downloading === model.id ? 'Downloading...' : 'Download'}
                  </button>
                )}
              </div>
            ))}
          </div>
        </section>

        {/* Audio Settings */}
        <section className="bg-gray-800 rounded-lg p-6 mb-6">
          <h3 className="text-lg font-medium text-white mb-4">Audio</h3>
          
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Sample Rate
              </label>
              <select className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white">
                <option value="44100">44.1 kHz</option>
                <option value="48000">48 kHz</option>
                <option value="96000">96 kHz</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Buffer Size
              </label>
              <select className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-white">
                <option value="256">256 samples</option>
                <option value="512">512 samples</option>
                <option value="1024">1024 samples</option>
                <option value="2048">2048 samples</option>
              </select>
            </div>
          </div>
        </section>

        {/* Storage */}
        <section className="bg-gray-800 rounded-lg p-6">
          <h3 className="text-lg font-medium text-white mb-4">Storage</h3>
          
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-white">Projects Location</p>
                <p className="text-sm text-gray-400">~/ghatana/speech/ai-voice/projects</p>
              </div>
              <button className="px-3 py-1.5 text-sm rounded-lg bg-gray-700 text-white hover:bg-gray-600 transition-all">
                Change
              </button>
            </div>

            <div className="flex items-center justify-between">
              <div>
                <p className="text-white">Models Location</p>
                <p className="text-sm text-gray-400">~/ghatana/speech/ai-voice/models</p>
              </div>
              <button className="px-3 py-1.5 text-sm rounded-lg bg-gray-700 text-white hover:bg-gray-600 transition-all">
                Change
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};
