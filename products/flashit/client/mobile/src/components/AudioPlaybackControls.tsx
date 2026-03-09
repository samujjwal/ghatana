import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, Text } from 'react-native';
import { Audio } from 'expo-av';
import * as FileSystem from 'expo-file-system/legacy';

interface AudioPlaybackControlsProps {
    audioUri: string;
    duration: number;
    onSoundLoaded: (sound: Audio.Sound) => void;
}

/**
 * Audio Playback Controls Component
 * 
 * @doc.type component
 * @doc.purpose Audio playback controls for recorded voice notes
 * @doc.layer product
 * @doc.pattern Component
 */
export const AudioPlaybackControls: React.FC<AudioPlaybackControlsProps> = ({
    audioUri,
    duration,
    onSoundLoaded,
}) => {
    const [sound, setSound] = useState<Audio.Sound | null>(null);
    const [isPlaying, setIsPlaying] = useState(false);
    const [position, setPosition] = useState(0);

    useEffect(() => {
        loadSound();
        return () => {
            if (sound) {
                sound.unloadAsync();
            }
        };
    }, [audioUri]);

    const loadSound = async () => {
        try {
            // Validate audio URI before attempting to load
            if (!audioUri || typeof audioUri !== 'string') {
                console.error('Invalid audio URI:', audioUri);
                return;
            }

            const { sound: audioSound } = await Audio.Sound.createAsync(
                { uri: audioUri },
                {},
                (status) => {
                    if (status.isLoaded) {
                        setPosition(status.positionMillis || 0);
                        setIsPlaying(status.isPlaying || false);
                    }
                }
            );
            setSound(audioSound);
            onSoundLoaded(audioSound);
        } catch (error) {
            console.error('Error loading sound:', error);
            console.error('Audio URI that failed:', audioUri);
            // Don't set sound if loading failed
            setSound(null);
        }
    };

    const togglePlayback = async () => {
        if (!sound) return;

        try {
            if (isPlaying) {
                await sound.pauseAsync();
            } else {
                await sound.playAsync();
            }
            setIsPlaying(!isPlaying);
        } catch (error) {
            console.error('Error toggling playback:', error);
        }
    };

    const formatDuration = (ms: number): string => {
        const seconds = Math.floor((ms / 1000) % 60);
        const minutes = Math.floor((ms / (1000 * 60)) % 60);
        return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    };

    return (
        <View style={styles.container}>
            <View style={styles.waveformPlaceholder}>
                <Text style={styles.placeholderText}>Audio Waveform</Text>
            </View>

            <View style={styles.controls}>
                <TouchableOpacity style={styles.playButton} onPress={togglePlayback}>
                    <Text style={styles.playButtonText}>
                        {isPlaying ? '⏸' : '▶'}
                    </Text>
                </TouchableOpacity>

                <View style={styles.timeDisplay}>
                    <Text style={styles.timeText}>
                        {formatDuration(position)} / {formatDuration(duration)}
                    </Text>
                </View>
            </View>
        </View>
    );
};

const styles = StyleSheet.create({
    container: {
        width: '100%',
        alignItems: 'center',
    },
    waveformPlaceholder: {
        width: '100%',
        height: 120,
        backgroundColor: '#1a1a1a',
        borderRadius: 12,
        justifyContent: 'center',
        alignItems: 'center',
        marginBottom: 20,
    },
    placeholderText: {
        color: '#666',
        fontSize: 14,
    },
    controls: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 20,
    },
    playButton: {
        width: 60,
        height: 60,
        borderRadius: 30,
        backgroundColor: '#007aff',
        justifyContent: 'center',
        alignItems: 'center',
    },
    playButtonText: {
        fontSize: 24,
        color: '#fff',
    },
    timeDisplay: {
        flex: 1,
    },
    timeText: {
        color: '#fff',
        fontSize: 16,
        textAlign: 'center',
    },
});