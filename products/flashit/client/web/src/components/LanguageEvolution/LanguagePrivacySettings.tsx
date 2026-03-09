/**
 * Language Privacy Settings Component
 * Week 13 Day 65 - Harden privacy controls for derived insights
 */

import React, { useState } from 'react';
import {
  Shield,
  Lock,
  Eye,
  EyeOff,
  Download,
  Trash2,
  AlertCircle,
  CheckCircle,
  Info,
  Save,
} from 'lucide-react';

interface PrivacySettings {
  enableLanguageTracking: boolean;
  enableEmotionAnalysis: boolean;
  enableTopicShifts: boolean;
  enableExpressionPatterns: boolean;
  dataRetentionDays: number;
  allowAIProcessing: boolean;
  shareWithResearchers: boolean;
}

export const LanguagePrivacySettings: React.FC = () => {
  const [settings, setSettings] = useState<PrivacySettings>({
    enableLanguageTracking: true,
    enableEmotionAnalysis: true,
    enableTopicShifts: true,
    enableExpressionPatterns: true,
    dataRetentionDays: 365,
    allowAIProcessing: true,
    shareWithResearchers: false,
  });

  const [isSaving, setIsSaving] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const updateSetting = <K extends keyof PrivacySettings>(
    key: K,
    value: PrivacySettings[K]
  ) => {
    setSettings((prev) => ({ ...prev, [key]: value }));
    setSaveSuccess(false);
  };

  const handleSave = async () => {
    setIsSaving(true);
    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 1000));
    setIsSaving(false);
    setSaveSuccess(true);
    setTimeout(() => setSaveSuccess(false), 3000);
  };

  const handleExportData = () => {
    // Simulate data export
    const data = {
      settings,
      exportedAt: new Date().toISOString(),
      dataType: 'language_evolution_insights',
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `flashit_language_insights_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleDeleteData = async () => {
    if (!showDeleteConfirm) {
      setShowDeleteConfirm(true);
      return;
    }
    // Simulate API call to delete data
    await new Promise((resolve) => setTimeout(resolve, 1000));
    setShowDeleteConfirm(false);
    alert('Language evolution data deleted successfully');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="rounded-full bg-blue-100 p-2">
          <Shield className="h-6 w-6 text-blue-600" />
        </div>
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Language Privacy Controls</h2>
          <p className="text-sm text-gray-600">
            Manage how your language data is analyzed and stored
          </p>
        </div>
      </div>

      {/* Privacy Notice */}
      <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
        <div className="flex items-start gap-3">
          <Info className="h-5 w-5 text-blue-600 mt-0.5 flex-shrink-0" />
          <div className="text-sm text-blue-900">
            <p className="font-medium mb-1">Your Privacy Matters</p>
            <p className="text-blue-700">
              All language analysis happens securely on our servers. Your raw moments are never
              shared. Only aggregated, anonymized patterns are used for insights. You can disable
              any feature or delete all data at any time.
            </p>
          </div>
        </div>
      </div>

      {/* Master Toggle */}
      <div className="rounded-lg border border-gray-300 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Lock className="h-5 w-5 text-gray-600" />
            <div>
              <h3 className="font-semibold text-gray-900">Language Tracking</h3>
              <p className="text-sm text-gray-600">
                Enable analysis of your vocabulary and expression patterns
              </p>
            </div>
          </div>
          <label className="relative inline-flex cursor-pointer items-center">
            <input
              type="checkbox"
              checked={settings.enableLanguageTracking}
              onChange={(e) => updateSetting('enableLanguageTracking', e.target.checked)}
              className="peer sr-only"
            />
            <div className="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-blue-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300"></div>
          </label>
        </div>
      </div>

      {/* Granular Controls */}
      {settings.enableLanguageTracking && (
        <div className="space-y-4">
          <h3 className="font-semibold text-gray-900">Feature Controls</h3>

          {/* Emotion Analysis */}
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                {settings.enableEmotionAnalysis ? (
                  <Eye className="h-5 w-5 text-green-600" />
                ) : (
                  <EyeOff className="h-5 w-5 text-gray-400" />
                )}
                <div>
                  <h4 className="font-medium text-gray-900">Emotion Analysis</h4>
                  <p className="text-sm text-gray-600">Track emotional diversity and trends</p>
                </div>
              </div>
              <label className="relative inline-flex cursor-pointer items-center">
                <input
                  type="checkbox"
                  checked={settings.enableEmotionAnalysis}
                  onChange={(e) => updateSetting('enableEmotionAnalysis', e.target.checked)}
                  className="peer sr-only"
                />
                <div className="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-green-600 peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
              </label>
            </div>
          </div>

          {/* Topic Shifts */}
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                {settings.enableTopicShifts ? (
                  <Eye className="h-5 w-5 text-green-600" />
                ) : (
                  <EyeOff className="h-5 w-5 text-gray-400" />
                )}
                <div>
                  <h4 className="font-medium text-gray-900">Topic Shifts</h4>
                  <p className="text-sm text-gray-600">Detect changes in subject focus over time</p>
                </div>
              </div>
              <label className="relative inline-flex cursor-pointer items-center">
                <input
                  type="checkbox"
                  checked={settings.enableTopicShifts}
                  onChange={(e) => updateSetting('enableTopicShifts', e.target.checked)}
                  className="peer sr-only"
                />
                <div className="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-green-600 peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
              </label>
            </div>
          </div>

          {/* Expression Patterns */}
          <div className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                {settings.enableExpressionPatterns ? (
                  <Eye className="h-5 w-5 text-green-600" />
                ) : (
                  <EyeOff className="h-5 w-5 text-gray-400" />
                )}
                <div>
                  <h4 className="font-medium text-gray-900">Expression Patterns</h4>
                  <p className="text-sm text-gray-600">Identify trending words and phrases</p>
                </div>
              </div>
              <label className="relative inline-flex cursor-pointer items-center">
                <input
                  type="checkbox"
                  checked={settings.enableExpressionPatterns}
                  onChange={(e) => updateSetting('enableExpressionPatterns', e.target.checked)}
                  className="peer sr-only"
                />
                <div className="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-green-600 peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
              </label>
            </div>
          </div>
        </div>
      )}

      {/* Data Retention */}
      <div className="rounded-lg border border-gray-300 bg-white p-6 shadow-sm">
        <h3 className="font-semibold text-gray-900 mb-4">Data Retention</h3>
        <div className="space-y-3">
          <label className="text-sm font-medium text-gray-700">
            Keep language insights for: {settings.dataRetentionDays} days
          </label>
          <input
            type="range"
            min="30"
            max="730"
            step="30"
            value={settings.dataRetentionDays}
            onChange={(e) => updateSetting('dataRetentionDays', parseInt(e.target.value))}
            className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-blue-600"
          />
          <div className="flex justify-between text-xs text-gray-500">
            <span>30 days</span>
            <span>1 year</span>
            <span>2 years</span>
          </div>
          <p className="text-sm text-gray-600">
            Older insights will be automatically deleted after this period
          </p>
        </div>
      </div>

      {/* AI Processing */}
      <div className="rounded-lg border border-gray-300 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h3 className="font-semibold text-gray-900">AI Processing</h3>
            <p className="text-sm text-gray-600">
              Allow OpenAI GPT-4 to analyze your moments for deeper insights
            </p>
          </div>
          <label className="relative inline-flex cursor-pointer items-center">
            <input
              type="checkbox"
              checked={settings.allowAIProcessing}
              onChange={(e) => updateSetting('allowAIProcessing', e.target.checked)}
              className="peer sr-only"
            />
            <div className="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-blue-600 peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
          </label>
        </div>
        {!settings.allowAIProcessing && (
          <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3">
            <div className="flex items-start gap-2">
              <AlertCircle className="h-4 w-4 text-yellow-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-yellow-800">
                Disabling AI processing will limit insight quality and feature availability
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Research Sharing */}
      <div className="rounded-lg border border-gray-300 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-gray-900">Research Contribution</h3>
            <p className="text-sm text-gray-600">
              Share anonymized patterns to improve mental health research (optional)
            </p>
          </div>
          <label className="relative inline-flex cursor-pointer items-center">
            <input
              type="checkbox"
              checked={settings.shareWithResearchers}
              onChange={(e) => updateSetting('shareWithResearchers', e.target.checked)}
              className="peer sr-only"
            />
            <div className="peer h-6 w-11 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-purple-600 peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
          </label>
        </div>
      </div>

      {/* Data Management Actions */}
      <div className="rounded-lg border border-gray-300 bg-white p-6 shadow-sm">
        <h3 className="font-semibold text-gray-900 mb-4">Data Management</h3>
        <div className="flex flex-wrap gap-3">
          {/* Export Data */}
          <button
            onClick={handleExportData}
            className="flex items-center gap-2 rounded-lg border border-blue-600 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700 hover:bg-blue-100 transition-colors"
          >
            <Download className="h-4 w-4" />
            Export My Data
          </button>

          {/* Delete Data */}
          <button
            onClick={handleDeleteData}
            className={`flex items-center gap-2 rounded-lg border px-4 py-2 text-sm font-medium transition-colors ${
              showDeleteConfirm
                ? 'border-red-600 bg-red-600 text-white hover:bg-red-700'
                : 'border-red-600 bg-red-50 text-red-700 hover:bg-red-100'
            }`}
          >
            <Trash2 className="h-4 w-4" />
            {showDeleteConfirm ? 'Confirm Delete' : 'Delete All Insights'}
          </button>

          {showDeleteConfirm && (
            <button
              onClick={() => setShowDeleteConfirm(false)}
              className="text-sm text-gray-600 hover:text-gray-900 underline"
            >
              Cancel
            </button>
          )}
        </div>
      </div>

      {/* Save Button */}
      <div className="flex items-center justify-between rounded-lg border border-gray-300 bg-gray-50 p-4">
        {saveSuccess ? (
          <div className="flex items-center gap-2 text-green-700">
            <CheckCircle className="h-5 w-5" />
            <span className="font-medium">Settings saved successfully</span>
          </div>
        ) : (
          <p className="text-sm text-gray-600">Changes will apply immediately</p>
        )}
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          {isSaving ? (
            <>
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              Saving...
            </>
          ) : (
            <>
              <Save className="h-4 w-4" />
              Save Settings
            </>
          )}
        </button>
      </div>
    </div>
  );
};
