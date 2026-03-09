import React, { useState } from 'react';

export const TransportSettings: React.FC = () => {
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);

    try {
      // Placeholder for transport service initialization
      await new Promise((resolve) => setTimeout(resolve, 100));
    } catch (error) {
      console.error('Failed to save settings:', error);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Transport Settings</h2>

      <div className="flex items-center space-x-2">
        <span>Status:</span>
        <span className="font-medium text-gray-500">DISCONNECTED</span>
      </div>

      <form onSubmit={handleSave} className="space-y-4">
        <div>
          <label htmlFor="transport-type" className="block text-sm font-medium text-gray-700">
            Transport Type
          </label>
          <select
            id="transport-type"
            className="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
            value="file"
            disabled
          >
            <option value="file">File System (Default)</option>
          </select>
          <p className="mt-2 text-sm text-gray-500">
            File system transport is currently the only supported option.
          </p>
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={isSaving}
            className={`px-4 py-2 rounded-md text-white ${isSaving ? 'bg-gray-400 cursor-not-allowed' : 'bg-indigo-600 hover:bg-indigo-700'
              }`}
          >
            {isSaving ? 'Saving...' : 'Save Settings'}
          </button>
        </div>
      </form>
    </div>
  );
};

