/**
 * Bootstrap Preview Page
 *
 * @description Preview scaffold output before finalizing the bootstrap session.
 *
 * @doc.type page
 * @doc.purpose Bootstrap preview and confirmation
 * @doc.layer product
 */

import React from 'react';
import { useParams, NavLink } from 'react-router';
import { Eye, ArrowLeft, ArrowRight, CheckCircle2 } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const BootstrapPreviewPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-5xl mx-auto">
        {/* Header */}
        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-violet-500/10">
            <Eye className="w-6 h-6 text-violet-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Bootstrap Preview</h1>
            <p className="text-zinc-400">
              Review your project scaffold before finalizing
            </p>
          </div>
        </div>

        {/* Preview Content */}
        <div className="space-y-6">
          <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800">
            <h2 className="text-lg font-semibold mb-4">Project Structure</h2>
            <p className="text-zinc-400">
              Preview of the generated project structure will appear here once
              connected to the bootstrap service.
            </p>
          </div>

          <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800">
            <h2 className="text-lg font-semibold mb-4">Configuration Summary</h2>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div className="text-zinc-400">Tech Stack</div>
              <div>—</div>
              <div className="text-zinc-400">Template</div>
              <div>—</div>
              <div className="text-zinc-400">Agents Enabled</div>
              <div>—</div>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-between pt-4">
            <NavLink
              to={projectId ? ROUTES.bootstrap.root(projectId) : '/'}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-zinc-800 hover:bg-zinc-700 transition-colors text-sm"
            >
              <ArrowLeft className="w-4 h-4" />
              Back to Session
            </NavLink>
            <button className="flex items-center gap-2 px-6 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
              <CheckCircle2 className="w-4 h-4" />
              Finalize &amp; Continue
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BootstrapPreviewPage;
