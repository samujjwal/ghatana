/**
 * @doc.type component
 * @doc.purpose Computer Vision panel component
 * @doc.layer application
 * @doc.pattern service panel
 */

import React from 'react';
import { Card, Button, Loading } from '@ghatana/audio-video-ui';

const VisionPanel: React.FC = () => {
  const [selectedImage, setSelectedImage] = React.useState<string | null>(null);
  const [isProcessing, setIsProcessing] = React.useState(false);
  const [detections, setDetections] = React.useState<any[]>([]);

  const handleImageUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => setSelectedImage(e.target?.result as string);
      reader.readAsDataURL(file);
    }
  };

  const handleProcess = async () => {
    if (!selectedImage) return;
    
    setIsProcessing(true);
    try {
      // TODO: Call actual Vision service
      await new Promise(resolve => setTimeout(resolve, 1200));
      setDetections([
        { class: 'person', confidence: 0.92, bbox: { x: 100, y: 100, width: 200, height: 300 } },
        { class: 'car', confidence: 0.87, bbox: { x: 300, y: 200, width: 150, height: 100 } }
      ]);
    } catch (error) {
      console.error('Vision processing failed:', error);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="space-y-6">
      <Card title="Computer Vision" subtitle="Analyze images with AI">
        <div className="space-y-6">
          {/* Image Upload */}
          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Upload Image
            </label>
            <input
              type="file"
              accept="image/*"
              onChange={handleImageUpload}
              className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
            />
          </div>

          {/* Image Preview */}
          {selectedImage && (
            <div className="space-y-2">
              <h3 className="text-lg font-medium text-gray-900 dark:text-white">Preview</h3>
              <img
                src={selectedImage}
                alt="Selected"
                className="w-full max-w-md mx-auto rounded-lg border border-gray-300 dark:border-gray-600"
              />
            </div>
          )}

          {/* Process Button */}
          {selectedImage && (
            <Button
              onClick={handleProcess}
              disabled={isProcessing}
              className="w-full"
            >
              {isProcessing ? (
                <Loading size="sm" text="Analyzing image..." />
              ) : (
                'Analyze Image'
              )}
            </Button>
          )}

          {/* Results */}
          {detections.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-lg font-medium text-gray-900 dark:text-white">Detections</h3>
              <div className="space-y-2">
                {detections.map((detection, index) => (
                  <div key={index} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">
                    <span className="font-medium">{detection.class}</span>
                    <span className="text-sm text-gray-500">{(detection.confidence * 100).toFixed(1)}%</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};

export default VisionPanel;
