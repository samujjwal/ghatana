import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  CardHeader,
  CardContent,
  Button,
  Input,
  Badge,
  Select,
  SelectItem,
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
  Progress,
  Spinner,
} from '@ghatana/design-system';

interface VrExperiment {
  id: string;
  title: string;
  description: string;
  category: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  estimatedDuration: number;
  thumbnailUrl?: string;
  vrSupported: boolean;
  arSupported: boolean;
  desktopSupported: boolean;
  sessionCount: number;
}

interface VrCategory {
  name: string;
  count: number;
  icon: string;
}

interface VrSession {
  id: string;
  experiment: {
    id: string;
    title: string;
    category: string;
    thumbnailUrl?: string;
  };
  deviceType: 'vr' | 'ar' | 'desktop';
  status: 'active' | 'completed' | 'abandoned';
  duration: number;
  score?: number;
  startedAt: string;
  endedAt?: string;
}

/**
 * @doc.type component
 * @doc.purpose VR Labs catalog and session history page
 * @doc.layer product
 * @doc.pattern Page
 */
export function VrLabsPage() {
  const [activeTab, setActiveTab] = useState('catalog');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [difficulty, setDifficulty] = useState<string>('');
  const [search, setSearch] = useState('');

  // Fetch categories
  const { data: categoriesData } = useQuery({
    queryKey: ['vr-categories'],
    queryFn: async () => {
      const res = await fetch('/api/v1/vr/categories');
      return res.json();
    },
  });

  // Fetch experiments
  const { data: experimentsData, isLoading: experimentsLoading } = useQuery({
    queryKey: ['vr-experiments', selectedCategory, difficulty, search],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (selectedCategory) params.append('category', selectedCategory);
      if (difficulty) params.append('difficulty', difficulty);
      if (search) params.append('search', search);
      const res = await fetch(`/api/v1/vr/experiments?${params}`);
      return res.json();
    },
  });

  // Fetch session history
  const { data: sessionsData, isLoading: sessionsLoading } = useQuery({
    queryKey: ['vr-sessions'],
    queryFn: async () => {
      const res = await fetch('/api/v1/vr/sessions');
      return res.json();
    },
    enabled: activeTab === 'history',
  });

  const categories: VrCategory[] = categoriesData?.categories ?? [];
  const experiments: VrExperiment[] = experimentsData?.experiments ?? [];
  const sessions: VrSession[] = sessionsData?.sessions ?? [];

  const getDifficultyColor = (diff: string) => {
    switch (diff) {
      case 'beginner':
        return 'green';
      case 'intermediate':
        return 'yellow';
      case 'advanced':
        return 'red';
      default:
        return 'gray';
    }
  };

  const getDeviceIcon = (device: string) => {
    switch (device) {
      case 'vr':
        return '🥽';
      case 'ar':
        return '📱';
      case 'desktop':
        return '🖥️';
      default:
        return '🎮';
    }
  };

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}m ${secs}s`;
  };

  const startExperiment = async (experimentId: string, deviceType: 'vr' | 'ar' | 'desktop') => {
    // Check for WebXR support
    if (deviceType === 'vr' && navigator.xr) {
      const vrSupported = await navigator.xr.isSessionSupported('immersive-vr');
      if (!vrSupported) {
        alert('VR headset not detected. Falling back to desktop mode.');
        deviceType = 'desktop';
      }
    }

    // Navigate to VR session page
    window.location.href = `/vr/session/${experimentId}?device=${deviceType}`;
  };

  return (
    <div className="container mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
          🔬 Virtual Reality Labs
        </h1>
        <p className="text-gray-600 dark:text-gray-400">
          Explore science through immersive VR and AR experiments
        </p>
      </div>

      {/* VR Support Check */}
      <VrSupportBanner />

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="mb-6">
        <TabsList>
          <TabsTrigger value="catalog">🧪 Experiment Catalog</TabsTrigger>
          <TabsTrigger value="history">📊 My Sessions</TabsTrigger>
        </TabsList>

        {/* Catalog Tab */}
        <TabsContent value="catalog">
          {/* Categories */}
          <div className="flex flex-wrap gap-2 mb-6">
            <Button
              variant={selectedCategory === '' ? 'primary' : 'outline'}
              size="sm"
              onClick={() => setSelectedCategory('')}
            >
              All Categories
            </Button>
            {categories.map((cat) => (
              <Button
                key={cat.name}
                variant={selectedCategory === cat.name ? 'primary' : 'outline'}
                size="sm"
                onClick={() => setSelectedCategory(cat.name)}
              >
                {cat.icon} {cat.name} ({cat.count})
              </Button>
            ))}
          </div>

          {/* Filters */}
          <div className="flex flex-wrap gap-4 mb-6">
            <Input
              placeholder="Search experiments..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-64"
            />
            <Select
              value={difficulty}
              onValueChange={setDifficulty}
              placeholder="Difficulty"
            >
              <SelectItem value="">All Levels</SelectItem>
              <SelectItem value="beginner">🟢 Beginner</SelectItem>
              <SelectItem value="intermediate">🟡 Intermediate</SelectItem>
              <SelectItem value="advanced">🔴 Advanced</SelectItem>
            </Select>
          </div>

          {/* Experiments Grid */}
          {experimentsLoading ? (
            <div className="flex justify-center py-12">
              <Spinner size="lg" />
            </div>
          ) : experiments.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-gray-500 dark:text-gray-400">
                No experiments found. Try adjusting your filters.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {experiments.map((experiment) => (
                <ExperimentCard
                  key={experiment.id}
                  experiment={experiment}
                  onStart={startExperiment}
                  getDifficultyColor={getDifficultyColor}
                />
              ))}
            </div>
          )}
        </TabsContent>

        {/* History Tab */}
        <TabsContent value="history">
          {sessionsLoading ? (
            <div className="flex justify-center py-12">
              <Spinner size="lg" />
            </div>
          ) : sessions.length === 0 ? (
            <div className="text-center py-12">
              <div className="text-6xl mb-4">🥽</div>
              <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
                No VR Sessions Yet
              </h3>
              <p className="text-gray-500 dark:text-gray-400 mb-4">
                Start exploring experiments to see your session history here.
              </p>
              <Button onClick={() => setActiveTab('catalog')}>
                Browse Experiments
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              {sessions.map((session) => (
                <Card key={session.id}>
                  <CardContent className="flex items-center justify-between p-4">
                    <div className="flex items-center gap-4">
                      {session.experiment.thumbnailUrl ? (
                        <img
                          src={session.experiment.thumbnailUrl}
                          alt={session.experiment.title}
                          className="w-16 h-16 rounded-lg object-cover"
                        />
                      ) : (
                        <div className="w-16 h-16 rounded-lg bg-purple-100 dark:bg-purple-900 flex items-center justify-center text-2xl">
                          🔬
                        </div>
                      )}
                      <div>
                        <h3 className="font-semibold text-gray-900 dark:text-white">
                          {session.experiment.title}
                        </h3>
                        <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                          <span>{getDeviceIcon(session.deviceType)}</span>
                          <span>{new Date(session.startedAt).toLocaleDateString()}</span>
                          <span>•</span>
                          <span>{formatDuration(session.duration)}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      {session.score !== undefined && (
                        <div className="text-right">
                          <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">
                            {session.score}%
                          </div>
                          <div className="text-xs text-gray-500">Score</div>
                        </div>
                      )}
                      <Badge
                        variant={
                          session.status === 'completed'
                            ? 'success'
                            : session.status === 'active'
                            ? 'warning'
                            : 'secondary'
                        }
                      >
                        {session.status}
                      </Badge>
                      {session.status === 'active' && (
                        <Button
                          size="sm"
                          onClick={() =>
                            startExperiment(session.experiment.id, session.deviceType)
                          }
                        >
                          Resume
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}

/**
 * VR Support Banner Component
 */
function VrSupportBanner() {
  const [vrSupport, setVrSupport] = useState<{
    webxr: boolean;
    vr: boolean;
    ar: boolean;
  } | null>(null);

  useEffect(() => {
    async function checkVrSupport() {
      if (!navigator.xr) {
        setVrSupport({ webxr: false, vr: false, ar: false });
        return;
      }

      const [vrSupported, arSupported] = await Promise.all([
        navigator.xr.isSessionSupported('immersive-vr').catch(() => false),
        navigator.xr.isSessionSupported('immersive-ar').catch(() => false),
      ]);

      setVrSupport({
        webxr: true,
        vr: vrSupported,
        ar: arSupported,
      });
    }

    checkVrSupport();
  }, []);

  if (!vrSupport) return null;

  return (
    <Card className="mb-6 bg-gradient-to-r from-purple-50 to-indigo-50 dark:from-purple-900/20 dark:to-indigo-900/20 border-purple-200 dark:border-purple-800">
      <CardContent className="py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <span className="text-3xl">🥽</span>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">
                Device Capabilities
              </h3>
              <div className="flex items-center gap-4 text-sm">
                <span className={vrSupport.vr ? 'text-green-600' : 'text-gray-400'}>
                  {vrSupport.vr ? '✓' : '✗'} VR Headset
                </span>
                <span className={vrSupport.ar ? 'text-green-600' : 'text-gray-400'}>
                  {vrSupport.ar ? '✓' : '✗'} AR Device
                </span>
                <span className="text-green-600">✓ Desktop Mode</span>
              </div>
            </div>
          </div>
          {!vrSupport.vr && !vrSupport.ar && (
            <Button variant="outline" size="sm" asChild>
              <a
                href="https://immersiveweb.dev/#supporttable"
                target="_blank"
                rel="noopener noreferrer"
              >
                Check Browser Support
              </a>
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * Experiment Card Component
 */
function ExperimentCard({
  experiment,
  onStart,
  getDifficultyColor,
}: {
  experiment: VrExperiment;
  onStart: (id: string, device: 'vr' | 'ar' | 'desktop') => void;
  getDifficultyColor: (diff: string) => string;
}) {
  const [showModes, setShowModes] = useState(false);

  return (
    <Card className="overflow-hidden hover:shadow-lg transition-shadow">
      {/* Thumbnail */}
      <div className="aspect-video relative bg-gradient-to-br from-purple-500 to-indigo-600">
        {experiment.thumbnailUrl ? (
          <img
            src={experiment.thumbnailUrl}
            alt={experiment.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="absolute inset-0 flex items-center justify-center text-6xl">
            🔬
          </div>
        )}
        <div className="absolute top-2 right-2 flex gap-1">
          {experiment.vrSupported && (
            <span className="bg-black/50 text-white px-2 py-0.5 rounded text-xs">
              VR
            </span>
          )}
          {experiment.arSupported && (
            <span className="bg-black/50 text-white px-2 py-0.5 rounded text-xs">
              AR
            </span>
          )}
        </div>
      </div>

      <CardContent className="p-4">
        <div className="flex items-start justify-between mb-2">
          <h3 className="font-semibold text-gray-900 dark:text-white line-clamp-2">
            {experiment.title}
          </h3>
          <Badge variant={getDifficultyColor(experiment.difficulty) as any} size="sm">
            {experiment.difficulty}
          </Badge>
        </div>

        <p className="text-sm text-gray-600 dark:text-gray-400 mb-3 line-clamp-2">
          {experiment.description}
        </p>

        <div className="flex items-center justify-between text-sm text-gray-500 dark:text-gray-400 mb-4">
          <span>⏱️ {experiment.estimatedDuration} mins</span>
          <span>👥 {experiment.sessionCount} sessions</span>
        </div>

        {showModes ? (
          <div className="space-y-2">
            <div className="text-sm text-gray-600 dark:text-gray-400 mb-2">
              Select mode:
            </div>
            <div className="flex gap-2">
              {experiment.vrSupported && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onStart(experiment.id, 'vr')}
                >
                  🥽 VR
                </Button>
              )}
              {experiment.arSupported && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onStart(experiment.id, 'ar')}
                >
                  📱 AR
                </Button>
              )}
              {experiment.desktopSupported && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => onStart(experiment.id, 'desktop')}
                >
                  🖥️ Desktop
                </Button>
              )}
            </div>
            <Button
              size="sm"
              variant="ghost"
              className="w-full mt-2"
              onClick={() => setShowModes(false)}
            >
              Cancel
            </Button>
          </div>
        ) : (
          <Button className="w-full" onClick={() => setShowModes(true)}>
            Start Experiment
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

export default VrLabsPage;
