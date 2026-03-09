/**
 * @doc.type component
 * @doc.purpose Speech-to-Text panel component
 * @doc.layer application
 * @doc.pattern service panel
 */

import React from 'react';
import { Card, Button, Input, Loading } from '@ghatana/audio-video-ui';

const STTPanel: React.FC = () => {
  const [isRecording, setIsRecording] = React.useState(false);
  const [transcription, setTranscription] = React.useState('');
  const [isProcessing, setIsProcessing] = React.useState(false);

  const handleStartRecording = () => {
    setIsRecording(true);
    // TODO: Implement actual recording
  };

  const handleStopRecording = async () => {
    setIsRecording(false);
    setIsProcessing(true);
    
    try {
      // TODO: Call actual STT service
      await new Promise(resolve => setTimeout(resolve, 1000));
      setTranscription("This is a sample transcription of the recorded audio.");
    } catch (error) {
      console.error('Transcription failed:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="space-y-6">
      <Card title="Speech to Text" subtitle="Convert speech to text using AI">
        <div className="space-y-6">
          {/* Recording Controls */}
          <div className="flex flex-col items-center space-y-4">
            <div className="audio-visualizer">
              {isRecording ? (
                <div className="flex space-x-1">
                  <div className="w-1 h-8 bg-blue-500 animate-pulse"></div>
                  <div className="w-1 h-12 bg-blue-500 animate-pulse-slow"></div>
                  <div className="w-1 h-6 bg-blue-500 animate-pulse"></div>
                  <div className="w-1 h-10 bg-blue-500 animate-pulse-slow"></div>
                  <div className="w-1 h-8 bg-blue-500 animate-pulse"></div>
                </div>
              ) : (
                <span className="text-gray-500">Click to start recording</span>
              )}
            </div>
            
            <Button
              onClick={isRecording ? handleStopRecording : handleStartRecording}
              variant={isRecording ? 'secondary' : 'primary'}
              disabled={isProcessing}
            >
              {isProcessing ? (
                <Loading size="sm" text="Processing..." />
              ) : isRecording ? (
                'Stop Recording'
              ) : (
                'Start Recording'
              )}
            </Button>
          </div>

          {/* Transcription Result */}
          {transcription && (
            <div className="space-y-2">
              <h3 className="text-lg font-medium text-gray-900 dark:text-white">Transcription</h3>
              <div className="text-display">
                {transcription}
              </div>
            </div>
          )}

          {/* Settings */}
          <div className="space-y-4">
            <h3 className="text-lg font-medium text-gray-900 dark:text-white">Settings</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Input
                label="Language"
                placeholder="e.g., en-US"
                defaultValue="en-US"
              />
              <Input
                label="Model"
                placeholder="e.g., whisper-tiny"
                defaultValue="whisper-tiny"
              />
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default STTPanel;
