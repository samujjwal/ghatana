/**
 * VoiceInput Component
 * 
 * Voice input component using Web Speech API for real-time transcription.
 * Supports push-to-talk and always-on modes with visual feedback.
 * 
 * Features:
 * - Real-time speech-to-text transcription
 * - Push-to-talk and continuous modes
 * - Visual waveform/level indicator
 * - Language selection
 * - Confidence scores
 * - Error handling and fallbacks
 * - Browser compatibility detection
 * 
 * @example
 * ```tsx
 * <VoiceInput
 *   onTranscript={(text) => console.log('Transcribed:', text)}
 *   mode="push-to-talk"
 *   language="en-US"
 * />
 * ```
 */

import { Mic as MicIcon, MicOff as MicOffIcon, Stop as StopIcon } from 'lucide-react';
import { Box, IconButton, Typography, Chip, Surface as Paper, LinearProgress, Select, MenuItem, FormControl, InputLabel, Tooltip, Alert } from '@ghatana/ui';
import { resolveMuiColor } from '../utils/safePalette';
import React, { useState, useEffect, useRef, useCallback } from 'react';

/**
 *
 */
export interface VoiceInputProps {
    /** Callback when transcript is received */
    onTranscript: (text: string, isFinal: boolean) => void;

    /** Callback when transcription completes */
    onComplete?: (text: string) => void;

    /** Callback when error occurs */
    onError?: (error: Error) => void;

    /** Voice input mode */
    mode?: 'push-to-talk' | 'continuous' | 'toggle';

    /** Language code (default: 'en-US') */
    language?: string;

    /** Show language selector */
    showLanguageSelector?: boolean;

    /** Show confidence scores */
    showConfidence?: boolean;

    /** Show visual level indicator */
    showLevelIndicator?: boolean;

    /** Enable interim results (real-time) */
    interimResults?: boolean;

    /** Auto-stop after silence (ms) */
    autoStopAfterSilence?: number;

    /** Custom styling */
    className?: string;

    /** Disabled state */
    disabled?: boolean;
}

/**
 *
 */
interface SpeechRecognitionEvent {
    results: {
        [index: number]: {
            [index: number]: {
                transcript: string;
                confidence: number;
            };
            isFinal: boolean;
            length: number;
        };
        length: number;
    };
    resultIndex: number;
}

// Web Speech API types
/**
 *
 */
interface SpeechRecognitionInterface extends EventTarget {
    continuous: boolean;
    interimResults: boolean;
    lang: string;
    start(): void;
    stop(): void;
    abort(): void;
    onstart: (() => void) | null;
    onend: (() => void) | null;
    onerror: ((event: { error: string; message?: string }) => void) | null;
    onresult: ((event: SpeechRecognitionEvent) => void) | null;
}

declare global {
    /**
     *
     */
    interface Window {
        SpeechRecognition: new () => SpeechRecognitionInterface;
        webkitSpeechRecognition: new () => SpeechRecognitionInterface;
    }
}

const SUPPORTED_LANGUAGES = [
    { code: 'en-US', name: 'English (US)' },
    { code: 'en-GB', name: 'English (UK)' },
    { code: 'es-ES', name: 'Spanish' },
    { code: 'fr-FR', name: 'French' },
    { code: 'de-DE', name: 'German' },
    { code: 'it-IT', name: 'Italian' },
    { code: 'pt-BR', name: 'Portuguese' },
    { code: 'ru-RU', name: 'Russian' },
    { code: 'ja-JP', name: 'Japanese' },
    { code: 'zh-CN', name: 'Chinese' },
    { code: 'ko-KR', name: 'Korean' },
    { code: 'ar-SA', name: 'Arabic' },
    { code: 'hi-IN', name: 'Hindi' },
];

export const VoiceInput: React.FC<VoiceInputProps> = ({
    onTranscript,
    onComplete,
    onError,
    mode = 'push-to-talk',
    language = 'en-US',
    showLanguageSelector = false,
    showConfidence = false,
    showLevelIndicator = true,
    interimResults = true,
    autoStopAfterSilence,
    className,
    disabled = false,
}) => {
    const [isListening, setIsListening] = useState(false);
    const [transcript, setTranscript] = useState('');
    const [confidence, setConfidence] = useState(0);
    const [selectedLanguage, setSelectedLanguage] = useState(language);
    const [isSupported, setIsSupported] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [audioLevel, setAudioLevel] = useState(0);

    const recognitionRef = useRef<SpeechRecognitionInterface | null>(null);
    const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);
    const analyserRef = useRef<AnalyserNode | null>(null);
    const animationFrameRef = useRef<number | null>(null);

    // Check browser support
    useEffect(() => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

        if (!SpeechRecognition) {
            setIsSupported(false);
            setError('Speech recognition not supported in this browser');
            return;
        }

        const recognition = new SpeechRecognition();
        recognition.continuous = mode === 'continuous' || mode === 'toggle';
        recognition.interimResults = interimResults;
        recognition.lang = selectedLanguage;

        recognition.onstart = () => {
            setIsListening(true);
            setError(null);
        };

        recognition.onend = () => {
            setIsListening(false);
            if (transcript) {
                onComplete?.(transcript);
            }
        };

        recognition.onerror = (event) => {
            const errorMsg = event.error || 'Unknown error';
            setError(errorMsg);
            setIsListening(false);
            onError?.(new Error(errorMsg));
        };

        recognition.onresult = (event: SpeechRecognitionEvent) => {
            let finalTranscript = '';
            let interimTranscript = '';

            for (let i = event.resultIndex; i < event.results.length; i++) {
                const result = event.results[i];
                const transcriptText = result[0].transcript;

                if (result.isFinal) {
                    finalTranscript += `${transcriptText} `;
                    setConfidence(result[0].confidence);
                } else {
                    interimTranscript += transcriptText;
                }
            }

            if (finalTranscript) {
                const newTranscript = transcript + finalTranscript;
                setTranscript(newTranscript);
                onTranscript(newTranscript, true);

                // Reset silence timer
                if (autoStopAfterSilence) {
                    resetSilenceTimer();
                }
            } else if (interimTranscript) {
                onTranscript(transcript + interimTranscript, false);
            }
        };

        recognitionRef.current = recognition;

        return () => {
            if (recognitionRef.current) {
                recognitionRef.current.abort();
            }
            stopAudioLevelMonitoring();
        };
    }, [selectedLanguage, mode, interimResults, autoStopAfterSilence]);

    const resetSilenceTimer = useCallback(() => {
        if (silenceTimerRef.current) {
            clearTimeout(silenceTimerRef.current);
        }

        if (autoStopAfterSilence) {
            silenceTimerRef.current = setTimeout(() => {
                stopListening();
            }, autoStopAfterSilence);
        }
    }, [autoStopAfterSilence]);

    const startListening = useCallback(() => {
        if (!recognitionRef.current || disabled) return;

        try {
            setTranscript('');
            setConfidence(0);
            recognitionRef.current.start();
            startAudioLevelMonitoring();

            if (autoStopAfterSilence) {
                resetSilenceTimer();
            }
        } catch (err) {
            console.error('Failed to start recognition:', err);
            setError('Failed to start voice input');
        }
    }, [disabled, autoStopAfterSilence, resetSilenceTimer]);

    const stopListening = useCallback(() => {
        if (!recognitionRef.current) return;

        try {
            recognitionRef.current.stop();
            stopAudioLevelMonitoring();

            if (silenceTimerRef.current) {
                clearTimeout(silenceTimerRef.current);
            }
        } catch (err) {
            console.error('Failed to stop recognition:', err);
        }
    }, []);

    const toggleListening = useCallback(() => {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }, [isListening, startListening, stopListening]);

    const startAudioLevelMonitoring = async () => {
        if (!showLevelIndicator) return;

        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            audioContextRef.current = new AudioContext();
            analyserRef.current = audioContextRef.current.createAnalyser();
            const source = audioContextRef.current.createMediaStreamSource(stream);
            source.connect(analyserRef.current);
            analyserRef.current.fftSize = 256;

            const updateLevel = () => {
                if (!analyserRef.current) return;

                const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
                analyserRef.current.getByteFrequencyData(dataArray);

                const average = dataArray.reduce((a, b) => a + b) / dataArray.length;
                setAudioLevel(Math.min(100, (average / 128) * 100));

                animationFrameRef.current = requestAnimationFrame(updateLevel);
            };

            updateLevel();
        } catch (err) {
            console.error('Failed to access microphone:', err);
        }
    };

    const stopAudioLevelMonitoring = () => {
        if (animationFrameRef.current) {
            cancelAnimationFrame(animationFrameRef.current);
        }
        if (audioContextRef.current) {
            audioContextRef.current.close();
        }
        setAudioLevel(0);
    };

    const handleLanguageChange = (newLanguage: string) => {
        setSelectedLanguage(newLanguage);
        if (recognitionRef.current) {
            recognitionRef.current.lang = newLanguage;
        }
    };

    if (!isSupported) {
        return (
            <Paper className={`p-4 ${className || ''}`}>
                <Alert severity="error">
                    Voice input is not supported in your browser. Please use Chrome, Edge, or Safari.
                </Alert>
            </Paper>
        );
    }

    const renderMicButton = () => {
        const handleMouseDown = () => {
            if (mode === 'push-to-talk') {
                startListening();
            }
        };

        const handleMouseUp = () => {
            if (mode === 'push-to-talk') {
                stopListening();
            }
        };

        const handleClick = () => {
            if (mode === 'toggle' || mode === 'continuous') {
                toggleListening();
            }
        };

        return (
            <Tooltip title={
                mode === 'push-to-talk'
                    ? 'Hold to speak'
                    : isListening
                        ? 'Stop listening'
                        : 'Start listening'
            }>
                <IconButton
                    onMouseDown={handleMouseDown}
                    onMouseUp={handleMouseUp}
                    onMouseLeave={handleMouseUp}
                    onClick={handleClick}
                    disabled={disabled}
                    color={isListening ? 'error' : 'primary'}
                    className={`w-16 h-16 ${isListening ? 'bg-red-200 hover:bg-red-500' : 'bg-blue-200 hover:bg-blue-500'}`}
                >
                    {isListening ? <MicIcon size={32} /> : <MicOffIcon size={32} />}
                </IconButton>
            </Tooltip>
        );
    };

    return (
        <Paper
            elevation={3}
            className={`p-6 flex flex-col items-center gap-4 ${className || ''}`}
        >
            {/* Language Selector */}
            {showLanguageSelector && (
                <FormControl fullWidth size="sm">
                    <InputLabel>Language</InputLabel>
                    <Select
                        value={selectedLanguage}
                        onChange={(e) => handleLanguageChange(e.target.value)}
                        disabled={isListening || disabled}
                        label="Language"
                    >
                        {SUPPORTED_LANGUAGES.map((lang) => (
                            <MenuItem key={lang.code} value={lang.code}>
                                {lang.name}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            )}

            {/* Microphone Button */}
            <Box className="flex items-center gap-4">
                {renderMicButton()}

                {isListening && mode === 'continuous' && (
                    <IconButton onClick={stopListening} color={resolveMuiColor(useTheme(), 'error', 'default') as unknown}>
                        <StopIcon />
                    </IconButton>
                )}
            </Box>

            {/* Status */}
            <Box className="flex gap-2 flex-wrap justify-center">
                <Chip
                    label={isListening ? 'Listening...' : 'Ready'}
                    color={resolveMuiColor(useTheme(), isListening ? 'error' : 'default', 'default') as unknown}
                    size="sm"
                />

                {mode === 'push-to-talk' && !isListening && (
                    <Chip label="Press & Hold" size="sm" variant="outlined" />
                )}

                {showConfidence && confidence > 0 && (
                        <Chip
                            label={`Confidence: ${Math.round(confidence * 100)}%`}
                            size="sm"
                            color={resolveMuiColor(useTheme(), 'success', 'default') as unknown}
                        />
                )}
            </Box>

            {/* Audio Level Indicator */}
            {showLevelIndicator && isListening && (
                <Box className="w-full">
                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary" gutterBottom>
                        Audio Level
                    </Typography>
                    <LinearProgress
                        variant="determinate"
                        value={audioLevel}
                        color={resolveMuiColor(useTheme(), audioLevel > 70 ? 'error' : audioLevel > 40 ? 'warning' : 'success', 'default') as unknown}
                    />
                </Box>
            )}

            {/* Transcript Preview */}
            {transcript && (
                <Box
                    className="w-full p-4 rounded bg-gray-100 dark:bg-gray-800 min-h-[60px]"
                >
                    <Typography as="p" className="text-sm" color="text.secondary">
                        {transcript}
                    </Typography>
                </Box>
            )}

            {/* Error Display */}
            {error && (
                <Alert severity="error" className="w-full">
                    {error}
                </Alert>
            )}

            {/* Mode Hint */}
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" textAlign="center">
                {mode === 'push-to-talk' && 'Press and hold the microphone button to speak'}
                {mode === 'toggle' && 'Click to start/stop listening'}
                {mode === 'continuous' && 'Continuous listening mode'}
            </Typography>
        </Paper>
    );
};
