import { useState } from 'react';
import type { Policy } from './PolicyManagement';

interface PolicyFormProps {
  policy?: Policy;
  onSubmit: (data: Omit<Policy, 'createdAt' | 'updatedAt'>) => void;
  onCancel: () => void;
}

export function PolicyForm({ policy, onSubmit, onCancel }: PolicyFormProps) {
  const [name, setName] = useState(policy?.name || '');
  const [type, setType] = useState<Policy['type']>(policy?.type || 'time-limit');
  const [maxUsageMinutes, setMaxUsageMinutes] = useState(policy?.restrictions.maxUsageMinutes?.toString() || '');
  const [blockedCategories, setBlockedCategories] = useState(
    policy?.restrictions.blockedCategories?.join(', ') || ''
  );
  const [blockedApps, setBlockedApps] = useState(policy?.restrictions.blockedApps?.join(', ') || '');
  const [allowedStart, setAllowedStart] = useState(policy?.restrictions.allowedHours?.start || '');
  const [allowedEnd, setAllowedEnd] = useState(policy?.restrictions.allowedHours?.end || '');
  const [deviceIds, setDeviceIds] = useState(policy?.deviceIds.join(', ') || '');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const newErrors: Record<string, string> = {};

    if (!name.trim()) {
      newErrors.name = 'Policy name is required';
    }

    if (type === 'time-limit' && (!maxUsageMinutes || parseInt(maxUsageMinutes) <= 0)) {
      newErrors.maxUsageMinutes = 'Valid max usage minutes required';
    }

    if (type === 'content-filter' && !blockedCategories.trim()) {
      newErrors.blockedCategories = 'At least one blocked category required';
    }

    if (type === 'app-block' && !blockedApps.trim()) {
      newErrors.blockedApps = 'At least one blocked app required';
    }

    if (type === 'schedule') {
      if (!allowedStart || !allowedEnd) {
        newErrors.allowedHours = 'Both start and end times required';
      }
    }

    if (!deviceIds.trim()) {
      newErrors.deviceIds = 'At least one device ID required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!validate()) {
      return;
    }

    const restrictions: Policy['restrictions'] = {};

    if (type === 'time-limit') {
      restrictions.maxUsageMinutes = parseInt(maxUsageMinutes);
    } else if (type === 'content-filter') {
      restrictions.blockedCategories = blockedCategories.split(',').map(c => c.trim()).filter(Boolean);
    } else if (type === 'app-block') {
      restrictions.blockedApps = blockedApps.split(',').map(a => a.trim()).filter(Boolean);
    } else if (type === 'schedule') {
      restrictions.allowedHours = { start: allowedStart, end: allowedEnd };
    }

    interface PolicyData {
      name: string;
      type: 'time-limit' | 'content-filter' | 'app-block' | 'schedule';
      restrictions: Record<string, unknown>;
      deviceIds: string[];
      id?: string;
    }

    const policyData: PolicyData = {
      name,
      type: type as 'time-limit' | 'content-filter' | 'app-block' | 'schedule',
      restrictions,
      deviceIds: deviceIds.split(',').map(d => d.trim()).filter(Boolean),
    };

    if (policy) {
      policyData.id = policy.id;
    }

    onSubmit(policyData as Omit<Policy, 'createdAt' | 'updatedAt'>);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1">
          Policy Name
        </label>
        <input
          id="name"
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
            errors.name ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
          }`}
          placeholder="e.g., School Time Limits"
        />
        {errors.name && <p className="mt-1 text-sm text-red-600">{errors.name}</p>}
      </div>

      <div>
        <label htmlFor="type" className="block text-sm font-medium text-gray-700 mb-1">
          Policy Type
        </label>
        <select
          id="type"
          value={type}
          onChange={(e) => setType(e.target.value as Policy['type'])}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="time-limit">Time Limit</option>
          <option value="content-filter">Content Filter</option>
          <option value="app-block">App Block</option>
          <option value="schedule">Schedule</option>
        </select>
      </div>

      {type === 'time-limit' && (
        <div>
          <label htmlFor="maxUsageMinutes" className="block text-sm font-medium text-gray-700 mb-1">
            Maximum Usage (minutes)
          </label>
          <input
            id="maxUsageMinutes"
            type="number"
            value={maxUsageMinutes}
            onChange={(e) => setMaxUsageMinutes(e.target.value)}
            className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
              errors.maxUsageMinutes ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
            }`}
            placeholder="60"
          />
          {errors.maxUsageMinutes && <p className="mt-1 text-sm text-red-600">{errors.maxUsageMinutes}</p>}
        </div>
      )}

      {type === 'content-filter' && (
        <div>
          <label htmlFor="blockedCategories" className="block text-sm font-medium text-gray-700 mb-1">
            Blocked Categories (comma-separated)
          </label>
          <input
            id="blockedCategories"
            type="text"
            value={blockedCategories}
            onChange={(e) => setBlockedCategories(e.target.value)}
            className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
              errors.blockedCategories ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
            }`}
            placeholder="social media, gaming, adult content"
          />
          {errors.blockedCategories && <p className="mt-1 text-sm text-red-600">{errors.blockedCategories}</p>}
        </div>
      )}

      {type === 'app-block' && (
        <div>
          <label htmlFor="blockedApps" className="block text-sm font-medium text-gray-700 mb-1">
            Blocked Apps (comma-separated)
          </label>
          <input
            id="blockedApps"
            type="text"
            value={blockedApps}
            onChange={(e) => setBlockedApps(e.target.value)}
            className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
              errors.blockedApps ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
            }`}
            placeholder="TikTok, Instagram, Snapchat"
          />
          {errors.blockedApps && <p className="mt-1 text-sm text-red-600">{errors.blockedApps}</p>}
        </div>
      )}

      {type === 'schedule' && (
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="allowedStart" className="block text-sm font-medium text-gray-700 mb-1">
              Start Time
            </label>
            <input
              id="allowedStart"
              type="time"
              value={allowedStart}
              onChange={(e) => setAllowedStart(e.target.value)}
              className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
                errors.allowedHours ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
              }`}
            />
          </div>
          <div>
            <label htmlFor="allowedEnd" className="block text-sm font-medium text-gray-700 mb-1">
              End Time
            </label>
            <input
              id="allowedEnd"
              type="time"
              value={allowedEnd}
              onChange={(e) => setAllowedEnd(e.target.value)}
              className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
                errors.allowedHours ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
              }`}
            />
          </div>
          {errors.allowedHours && <p className="col-span-2 text-sm text-red-600">{errors.allowedHours}</p>}
        </div>
      )}

      <div>
        <label htmlFor="deviceIds" className="block text-sm font-medium text-gray-700 mb-1">
          Device IDs (comma-separated)
        </label>
        <input
          id="deviceIds"
          type="text"
          value={deviceIds}
          onChange={(e) => setDeviceIds(e.target.value)}
          className={`w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 ${
            errors.deviceIds ? 'border-red-500 focus:ring-red-500' : 'border-gray-300 focus:ring-blue-500'
          }`}
          placeholder="device-1, device-2"
        />
        {errors.deviceIds && <p className="mt-1 text-sm text-red-600">{errors.deviceIds}</p>}
      </div>

      <div className="flex gap-3 pt-4">
        <button
          type="submit"
          className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {policy ? 'Update Policy' : 'Create Policy'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
