import React, { useState, useRef } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Text,
  Dimensions,
} from 'react-native';
import { VideoView, useVideoPlayer } from 'expo-video';

const { width, height } = Dimensions.get('window');

interface VideoPreviewProps {
  videoUri: string;
  duration: number;
  onDelete: () => void;
  onSave: () => void;
}

/**
 * Video Preview Component
 * 
 * @doc.type component
 * @doc.purpose Video playback preview with save/delete controls
 * @doc.layer product
 * @doc.pattern Component
 */
export const VideoPreview: React.FC<VideoPreviewProps> = ({
  videoUri,
  duration,
  onDelete,
  onSave,
}) => {
  const videoRef = useRef<VideoView>(null);
  const player = useVideoPlayer(videoUri);

  const [isPlaying, setIsPlaying] = useState(false);
  const [position, setPosition] = useState(0);

  // Update state based on player events
  React.useEffect(() => {
    if (player) {
      const playingSubscription = player.addListener('playingChange', (event) => {
        setIsPlaying(event.isPlaying);
      });

      const timeUpdateSubscription = player.addListener('timeUpdate', (event) => {
        setPosition(event.currentTime * 1000); // Convert to milliseconds
      });

      const playToEndSubscription = player.addListener('playToEnd', () => {
        // Auto-replay when video ends
        if (player) {
          player.currentTime = 0;
          player.play();
        }
      });

      return () => {
        playingSubscription?.remove();
        timeUpdateSubscription?.remove();
        playToEndSubscription?.remove();
      };
    }
  }, [player]);

  const togglePlayback = async () => {
    if (!player) return;

    if (isPlaying) {
      player.pause();
    } else {
      player.play();
    }
  };

  const formatTime = (ms: number): string => {
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor((ms / (1000 * 60)) % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <View style={styles.container}>
      {/* Video Player */}
      <View style={styles.videoContainer}>
        <VideoView
          ref={videoRef}
          player={player}
          style={styles.video}
          contentFit="contain"
          nativeControls={false}
        />

        {/* Play/Pause Overlay */}
        <TouchableOpacity
          style={styles.playOverlay}
          onPress={togglePlayback}
          activeOpacity={0.9}
        >
          {!isPlaying && (
            <View style={styles.playButton}>
              <Text style={styles.playButtonText}>▶</Text>
            </View>
          )}
        </TouchableOpacity>

        {/* Progress Bar */}
        <View style={styles.progressContainer}>
          <View style={styles.progressBackground}>
            <View
              style={[
                styles.progressBar,
                {
                  width: `${duration > 0 ? (position / duration) * 100 : 0}%`,
                },
              ]}
            />
          </View>
          <Text style={styles.timeText}>
            {formatTime(position)} / {formatTime(duration)}
          </Text>
        </View>
      </View>

      {/* Controls */}
      <View style={styles.controls}>
        <TouchableOpacity style={styles.deleteButton} onPress={onDelete}>
          <Text style={styles.deleteButtonText}>Delete</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.saveButton} onPress={onSave}>
          <Text style={styles.saveButtonText}>Save & Use</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  videoContainer: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
    position: 'relative',
  },
  video: {
    width: width,
    height: height * 0.7,
  },
  playOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  playButton: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  playButtonText: {
    fontSize: 36,
    color: '#fff',
    marginLeft: 8,
  },
  progressContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 16,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  progressBackground: {
    height: 4,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 2,
    overflow: 'hidden',
    marginBottom: 8,
  },
  progressBar: {
    height: '100%',
    backgroundColor: '#007aff',
    borderRadius: 2,
  },
  timeText: {
    fontSize: 12,
    color: '#fff',
    textAlign: 'center',
    fontVariant: ['tabular-nums'],
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 20,
    backgroundColor: '#000',
  },
  deleteButton: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    backgroundColor: '#333',
    marginRight: 10,
    alignItems: 'center',
  },
  deleteButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#ff3b30',
  },
  saveButton: {
    flex: 1,
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    backgroundColor: '#007aff',
    marginLeft: 10,
    alignItems: 'center',
  },
  saveButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#fff',
  },
});
