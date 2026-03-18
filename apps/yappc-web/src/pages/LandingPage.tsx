import React from 'react';
import { Link } from 'react-router';

/**
 * LandingPage — unauthenticated marketing/entry page.
 *
 * @doc.type component
 * @doc.purpose Public landing page with CTA
 * @doc.layer product
 */
const LandingPage: React.FC = () => {
  return (
    <div className="flex min-h-screen flex-col bg-zinc-950 text-zinc-100">
      {/* Nav */}
      <nav className="flex items-center justify-between border-b border-zinc-800 px-6 py-4">
        <span className="text-lg font-bold tracking-tight">YAPPC</span>
        <div className="flex items-center gap-4">
          <Link to="/auth/login" className="text-sm text-zinc-400 hover:text-zinc-200">
            Sign in
          </Link>
          <Link
            to="/auth/register"
            className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-500"
          >
            Get started
          </Link>
        </div>
      </nav>

      {/* Hero */}
      <main className="flex flex-1 flex-col items-center justify-center px-6 text-center">
        <h1 className="max-w-2xl text-4xl font-extrabold leading-tight sm:text-5xl">
          Ship software faster,
          <br />
          <span className="text-blue-400">securely</span>
        </h1>
        <p className="mt-4 max-w-lg text-lg text-zinc-400">
          YAPPC is the AI-native platform for project management, DevSecOps, and
          collaborative development — all on one canvas.
        </p>

        <div className="mt-8 flex gap-4">
          <Link
            to="/auth/register"
            className="rounded-md bg-blue-600 px-6 py-2.5 text-sm font-semibold text-white shadow hover:bg-blue-500"
          >
            Start free trial
          </Link>
          <Link
            to="/docs"
            className="rounded-md border border-zinc-700 bg-zinc-900 px-6 py-2.5 text-sm font-semibold text-zinc-300 hover:bg-zinc-800"
          >
            Documentation
          </Link>
        </div>

        {/* Feature highlights */}
        <div className="mt-16 grid max-w-4xl grid-cols-1 gap-8 sm:grid-cols-3">
          {[
            {
              title: 'Canvas-First',
              desc: 'Visual project planning on an infinite canvas with real-time collaboration.',
            },
            {
              title: 'AI-Native',
              desc: 'AI agents assist with requirements, code review, and security scanning.',
            },
            {
              title: 'DevSecOps Built-in',
              desc: 'Compliance, vulnerability tracking, and incident response — integrated.',
            },
          ].map((f) => (
            <div
              key={f.title}
              className="rounded-lg border border-zinc-800 bg-zinc-900/50 p-6 text-left"
            >
              <h3 className="text-sm font-semibold text-zinc-200">{f.title}</h3>
              <p className="mt-2 text-sm text-zinc-400">{f.desc}</p>
            </div>
          ))}
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-zinc-800 px-6 py-4 text-center text-xs text-zinc-600">
        &copy; {new Date().getFullYear()} Ghatana Technologies. All rights reserved.
      </footer>
    </div>
  );
};

export default LandingPage;
