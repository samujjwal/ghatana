export interface Config {
  monitoring: {
    enabled: boolean;
    samplingRate: number;
    domains: string[];
  };
  output: {
    directory: string;
    maxFileSizeMB: number;
    maxFiles: number;
  };
  metrics: {
    collectPerformance: boolean;
    collectNetwork: boolean;
    collectErrors: boolean;
  };
}

export const defaultConfig: Config = {
  monitoring: {
    enabled: true,
    samplingRate: 0.1,
    domains: [],
  },
  output: {
    directory: 'dcmaar_logs',
    maxFileSizeMB: 10,
    maxFiles: 10,
  },
  metrics: {
    collectPerformance: true,
    collectNetwork: true,
    collectErrors: true,
  },
};

export async function readConfigFile(file: File): Promise<Config> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const config = JSON.parse(e.target?.result as string) as Partial<Config>;
        resolve({ ...defaultConfig, ...config });
      } catch {
        reject(new Error('Invalid configuration file format'));
      }
    };
    reader.onerror = () => reject(new Error('Error reading configuration file'));
    reader.readAsText(file);
  });
}

export async function writeOutputFile(data: unknown, filename: string): Promise<void> {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } finally {
    URL.revokeObjectURL(url);
  }
}

export async function exportData(data: unknown, filename: string): Promise<boolean> {
  try {
    await writeOutputFile(data, filename);
    return true;
  } catch (err) {
    // Keep a narrow console in test helpers only
    // Mark `err` as intentionally unused for linters when console is unavailable
    void err;
    try { console.error('Export failed:', err); } catch {}
    return false;
  }
}
