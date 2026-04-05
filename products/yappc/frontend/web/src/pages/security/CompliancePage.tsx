/**
 * Compliance Page
 *
 * @description Compliance framework tracking (SOC2, HIPAA, GDPR) with
 * control status, evidence collection, and gap analysis.
 *
 * @doc.type page
 * @doc.purpose Compliance management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Shield, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';

const frameworks = [
  { id: 'soc2', label: 'SOC 2', controls: 0, passing: 0 },
  { id: 'hipaa', label: 'HIPAA', controls: 0, passing: 0 },
  { id: 'gdpr', label: 'GDPR', controls: 0, passing: 0 },
  { id: 'iso27001', label: 'ISO 27001', controls: 0, passing: 0 },
];

const CompliancePage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-blue-500/10">
            <Shield className="w-6 h-6 text-blue-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Compliance</h1>
            <p className="text-zinc-400">Framework adherence and control tracking</p>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {frameworks.map((fw) => (
            <div key={fw.id} className="p-6 rounded-xl bg-zinc-900 border border-zinc-800 hover:border-zinc-700 transition-colors cursor-pointer">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold">{fw.label}</h3>
                <span className="text-xs text-zinc-500">
                  {fw.passing}/{fw.controls} controls
                </span>
              </div>
              <div className="w-full h-2 rounded-full bg-zinc-800">
                <div className="h-2 rounded-full bg-zinc-600" style={{ width: '0%' }} />
              </div>
              <p className="text-xs text-zinc-500 mt-3">No controls configured</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default CompliancePage;
