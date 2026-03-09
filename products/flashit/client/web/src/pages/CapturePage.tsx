/**
 * Capture Page
 * Primary interface for capturing text, audio, video, and image Moments
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAtom } from 'jotai';
import { selectedSphereIdAtom } from '../store/atoms';
import { useCreateMoment, useSpheres, useClassifySphereAI } from '../hooks/use-api';
import Layout from '../components/Layout';
import SphereSelector from '../components/SphereSelector';
import VoiceRecorder from '../components/media/VoiceRecorder';
import VideoRecorder from '../components/media/VideoRecorder';
import ImageCapture from '../components/media/ImageCapture';

const CAPTURE_TYPES = ['TEXT', 'VOICE', 'VIDEO', 'IMAGE'];
const EMOTION_OPTIONS = [
  'happy', 'sad', 'excited', 'anxious', 'calm', 'frustrated',
  'proud', 'grateful', 'angry', 'peaceful', 'energized', 'tired'
];

const TAG_SUGGESTIONS = [
  'work', 'personal', 'family', 'health', 'learning', 'creative',
  'social', 'reflection', 'goal', 'challenge', 'win', 'idea'
];

export default function CapturePage() {
  const [captureType, setCaptureType] = useState<string>('TEXT');
  const [text, setText] = useState('');
  const [mediaData, setMediaData] = useState<Blob | null>(null);
  const [selectedEmotions, setSelectedEmotions] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [customTag, setCustomTag] = useState('');
  const [importance, setImportance] = useState<number>(3);
  const [selectedSphereId, setSelectedSphereId] = useAtom(selectedSphereIdAtom);
  const [suggestedSphereId, setSuggestedSphereId] = useState<string | null>(null);
  const [showSuggestion, setShowSuggestion] = useState(false);

  const navigate = useNavigate();
  const createMoment = useCreateMoment();
  const classifySphereAI = useClassifySphereAI();
  const { data: spheresData } = useSpheres();

  const toggleEmotion = (emotion: string) => {
    setSelectedEmotions(prev =>
      prev.includes(emotion)
        ? prev.filter(e => e !== emotion)
        : [...prev, emotion]
    );
  };

  const toggleTag = (tag: string) => {
    setSelectedTags(prev =>
      prev.includes(tag)
        ? prev.filter(t => t !== tag)
        : [...prev, tag]
    );
  };

  const addCustomTag = () => {
    if (customTag && !selectedTags.includes(customTag)) {
      setSelectedTags(prev => [...prev, customTag]);
      setCustomTag('');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validate based on capture type
    if (captureType === 'TEXT' && !text) {
      alert('Please enter some text');
      return;
    }
    if (['VOICE', 'VIDEO', 'IMAGE'].includes(captureType) && !mediaData) {
      alert(`Please record/capture ${captureType.toLowerCase()}`);
      return;
    }

    try {
      const contentData: any = {
        type: captureType,
      };

      if (captureType === 'TEXT') {
        contentData.text = text;
      } else {
        contentData.mediaBlob = mediaData;
      }

      let finalSphereId = selectedSphereId;

      // If sphere is not selected, use AI to classify it
      if (!finalSphereId) {
        try {
          const result = await classifySphereAI.mutateAsync({
            content: contentData,
            signals: {
              emotions: selectedEmotions,
              tags: selectedTags,
              importance,
            },
          });
          finalSphereId = result.sphereId;
        } catch (error) {
          console.warn('AI classification failed, proceeding without sphere assignment:', error);
          // Continue without sphere assignment - backend may handle it
        }
      }

      await createMoment.mutateAsync({
        sphereId: finalSphereId,
        content: contentData,
        signals: {
          emotions: selectedEmotions,
          tags: selectedTags,
          importance,
        },
        capturedAt: new Date().toISOString(),
      });

      // Reset form
      setText('');
      setMediaData(null);
      setCaptureType('TEXT');
      setSelectedEmotions([]);
      setSelectedTags([]);
      setImportance(3);
      setSuggestedSphereId(null);
      setShowSuggestion(false);

      // Navigate to moments page
      navigate('/moments');
    } catch (error) {
      console.error('Failed to capture moment:', error);
      alert('Failed to capture moment. Please try again.');
    }
  };

  return (
    <Layout>
      <div className="max-w-3xl mx-auto">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-gray-900">Capture a Moment</h1>
          <p className="mt-2 text-gray-600">
            What's on your mind? Record your thoughts, feelings, and experiences.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Sphere Selector */}
          <div className="card">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Sphere <span className="text-gray-500 text-xs font-normal">(Optional)</span>
            </label>
            <SphereSelector />
            {!selectedSphereId && (
              <div className="mt-3 p-3 bg-blue-50 border border-blue-200 rounded-lg">
                <p className="text-sm text-blue-700">
                  💡 <strong>AI-Powered:</strong> Leave the sphere empty and our AI will automatically classify your moment to the most relevant sphere based on its content and context.
                </p>
              </div>
            )}
          </div>

          {/* Capture Type Selector */}
          <div className="card">
            <label className="block text-sm font-medium text-gray-700 mb-3">
              What type of moment?
            </label>
            <div className="grid grid-cols-4 gap-2">
              {CAPTURE_TYPES.map(type => (
                <button
                  key={type}
                  type="button"
                  onClick={() => {
                    setCaptureType(type);
                    setMediaData(null);
                    setText('');
                  }}
                  className={`p-3 rounded-lg text-sm font-medium transition-colors ${captureType === type
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                  disabled={createMoment.isPending}
                >
                  {type === 'TEXT' && '📝 Text'}
                  {type === 'VOICE' && '🎤 Voice'}
                  {type === 'VIDEO' && '🎥 Video'}
                  {type === 'IMAGE' && '📸 Image'}
                </button>
              ))}
            </div>
          </div>

          {/* Content Input - Text */}
          {captureType === 'TEXT' && (
            <div className="card">
              <label htmlFor="text" className="block text-sm font-medium text-gray-700 mb-2">
                What's happening? *
              </label>
              <textarea
                id="text"
                required
                rows={8}
                className="input resize-none"
                placeholder="Write your moment here..."
                value={text}
                onChange={(e) => setText(e.target.value)}
                disabled={createMoment.isPending}
              />
              <p className="mt-2 text-sm text-gray-500">
                {text.length} characters
              </p>
            </div>
          )}

          {/* Content Input - Voice */}
          {captureType === 'VOICE' && (
            <div className="card">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Record your voice *
              </label>
              <VoiceRecorder onRecordingComplete={setMediaData} />
            </div>
          )}

          {/* Content Input - Video */}
          {captureType === 'VIDEO' && (
            <div className="card">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Record a video *
              </label>
              <VideoRecorder onRecordingComplete={setMediaData} />
            </div>
          )}

          {/* Content Input - Image */}
          {captureType === 'IMAGE' && (
            <div className="card">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Capture an image *
              </label>
              <ImageCapture onImageCapture={(blob) => setMediaData(blob)} />
            </div>
          )}

          {/* Emotions */}
          <div className="card">
            <label className="block text-sm font-medium text-gray-700 mb-3">
              How are you feeling?
            </label>
            <div className="flex flex-wrap gap-2">
              {EMOTION_OPTIONS.map(emotion => (
                <button
                  key={emotion}
                  type="button"
                  onClick={() => toggleEmotion(emotion)}
                  className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${selectedEmotions.includes(emotion)
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                  disabled={createMoment.isPending}
                >
                  {emotion}
                </button>
              ))}
            </div>
          </div>

          {/* Tags */}
          <div className="card">
            <label className="block text-sm font-medium text-gray-700 mb-3">
              Add tags
            </label>
            <div className="flex flex-wrap gap-2 mb-4">
              {TAG_SUGGESTIONS.map(tag => (
                <button
                  key={tag}
                  type="button"
                  onClick={() => toggleTag(tag)}
                  className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${selectedTags.includes(tag)
                    ? 'bg-primary-100 text-primary-700 border border-primary-300'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                  disabled={createMoment.isPending}
                >
                  #{tag}
                </button>
              ))}
            </div>

            {selectedTags.length > 0 && (
              <div className="mb-4">
                <p className="text-sm text-gray-600 mb-2">Selected tags:</p>
                <div className="flex flex-wrap gap-2">
                  {selectedTags.map(tag => (
                    <span
                      key={tag}
                      className="inline-flex items-center px-3 py-1 rounded-md bg-primary-600 text-white text-sm"
                    >
                      #{tag}
                      <button
                        type="button"
                        onClick={() => toggleTag(tag)}
                        className="ml-2 hover:text-primary-200"
                        disabled={createMoment.isPending}
                      >
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              </div>
            )}

            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Add custom tag..."
                className="input flex-1"
                value={customTag}
                onChange={(e) => setCustomTag(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), addCustomTag())}
                disabled={createMoment.isPending}
              />
              <button
                type="button"
                onClick={addCustomTag}
                className="btn-secondary"
                disabled={createMoment.isPending}
              >
                Add
              </button>
            </div>
          </div>

          {/* Importance */}
          <div className="card">
            <label className="block text-sm font-medium text-gray-700 mb-3">
              Importance (1-5)
            </label>
            <div className="flex items-center gap-4">
              <input
                type="range"
                min="1"
                max="5"
                value={importance}
                onChange={(e) => setImportance(Number(e.target.value))}
                className="flex-1"
                disabled={createMoment.isPending}
              />
              <span className="text-2xl font-bold text-primary-600 w-8 text-center">
                {importance}
              </span>
            </div>
            <div className="flex justify-between text-xs text-gray-500 mt-2">
              <span>Low</span>
              <span>High</span>
            </div>
          </div>

          {/* Submit */}
          <div className="flex gap-4">
            <button
              type="button"
              onClick={() => navigate('/')}
              className="btn-secondary flex-1"
              disabled={createMoment.isPending}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn-primary flex-1"
              disabled={createMoment.isPending || !selectedSphereId}
            >
              {createMoment.isPending ? 'Capturing...' : 'Capture Moment'}
            </button>
          </div>
        </form>
      </div>
    </Layout>
  );
}

