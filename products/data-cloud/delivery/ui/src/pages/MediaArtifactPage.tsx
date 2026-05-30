import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@ghatana/design-system';
import { Upload, FileVideo, FileAudio, Clock, Shield, Trash2 } from 'lucide-react';
import { useOperations } from '../contexts/OperationsContext';

/**
 * MediaArtifactPage - UI for managing audio-video media artifacts
 *
 * Provides lifecycle management for media artifacts including:
 * - Upload and ingestion
 * - Transcription and vision analysis jobs
 * - Consent and retention policy management
 * - Archive and deletion
 *
 * Integrates with OperationsContext for async job tracking.
 */
export function MediaArtifactPage() {
  const { t } = useTranslation();
  const { startJob, completeJob } = useOperations();
  const [selectedTab, setSelectedTab] = useState<'all' | 'audio' | 'video'>('all');

  const handleTranscription = (artifactId: string) => {
    const jobId = startJob(`Transcription: ${artifactId}`);
    // TODO: Call backend API to trigger transcription
    // On completion: completeJob(jobId, 'success', 'Transcription completed');
  };

  const handleVisionAnalysis = (artifactId: string) => {
    const jobId = startJob(`Vision Analysis: ${artifactId}`);
    // TODO: Call backend API to trigger vision analysis
    // On completion: completeJob(jobId, 'success', 'Analysis completed');
  };

  return (
    <div className="container mx-auto p-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold mb-2">{t('mediaArtifacts.title')}</h1>
        <p className="text-muted-foreground">{t('mediaArtifacts.description')}</p>
      </div>

      <div className="mb-6 flex gap-2">
        <Button
          variant={selectedTab === 'all' ? 'default' : 'outline'}
          onClick={() => setSelectedTab('all')}
        >
          {t('mediaArtifacts.tabs.all')}
        </Button>
        <Button
          variant={selectedTab === 'audio' ? 'default' : 'outline'}
          onClick={() => setSelectedTab('audio')}
        >
          <FileAudio className="mr-2 h-4 w-4" />
          {t('mediaArtifacts.tabs.audio')}
        </Button>
        <Button
          variant={selectedTab === 'video' ? 'default' : 'outline'}
          onClick={() => setSelectedTab('video')}
        >
          <FileVideo className="mr-2 h-4 w-4" />
          {t('mediaArtifacts.tabs.video')}
        </Button>
        <div className="ml-auto">
          <Button>
            <Upload className="mr-2 h-4 w-4" />
            {t('mediaArtifacts.actions.upload')}
          </Button>
        </div>
      </div>

      <div className="rounded-md border">
        <div className="p-4">
          <div className="text-center py-12 text-muted-foreground">
            <FileVideo className="mx-auto h-12 w-12 mb-4 opacity-50" />
            <p>{t('mediaArtifacts.empty')}</p>
          </div>
        </div>
      </div>

      <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="rounded-md border p-4">
          <div className="flex items-center gap-2 mb-2">
            <Clock className="h-4 w-4 text-muted-foreground" />
            <h3 className="font-semibold">{t('mediaArtifacts.stats.pendingJobs')}</h3>
          </div>
          <p className="text-2xl font-bold">0</p>
        </div>
        <div className="rounded-md border p-4">
          <div className="flex items-center gap-2 mb-2">
            <Shield className="h-4 w-4 text-muted-foreground" />
            <h3 className="font-semibold">{t('mediaArtifacts.stats.retentionAlerts')}</h3>
          </div>
          <p className="text-2xl font-bold">0</p>
        </div>
        <div className="rounded-md border p-4">
          <div className="flex items-center gap-2 mb-2">
            <Trash2 className="h-4 w-4 text-muted-foreground" />
            <h3 className="font-semibold">{t('mediaArtifacts.stats.expiringSoon')}</h3>
          </div>
          <p className="text-2xl font-bold">0</p>
        </div>
      </div>
    </div>
  );
}
