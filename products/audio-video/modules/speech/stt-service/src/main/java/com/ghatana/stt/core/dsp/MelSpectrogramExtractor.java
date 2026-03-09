package com.ghatana.stt.core.dsp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mel spectrogram feature extractor for speech processing.
 *
 * <p>Converts audio waveforms to mel-scale spectrograms suitable for
 * speech recognition models. Uses Short-Time Fourier Transform (STFT)
 * with mel filterbanks.
 *
 * <p><b>Parameters (Whisper defaults):</b>
 * <ul>
 *   <li>Sample rate: 16000 Hz</li>
 *   <li>FFT size: 400 samples (25ms)</li>
 *   <li>Hop length: 160 samples (10ms)</li>
 *   <li>Mel bins: 80</li>
 *   <li>Frequency range: 0-8000 Hz</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Audio feature extraction
 * @doc.layer dsp
 * @doc.pattern Utility
 */
public final class MelSpectrogramExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(MelSpectrogramExtractor.class);

    private final int sampleRate;
    private final int nFft;
    private final int hopLength;
    private final int nMels;
    private final float fMin;
    private final float fMax;
    private final float[][] melFilterbank;
    private final float[] window;

    /**
     * Creates a mel spectrogram extractor with default Whisper parameters.
     */
    public MelSpectrogramExtractor() {
        this(16000, 400, 160, 80);
    }

    /**
     * Creates a mel spectrogram extractor with custom parameters.
     *
     * @param sampleRate audio sample rate in Hz
     * @param nFft FFT window size in samples
     * @param hopLength hop length between frames in samples
     * @param nMels number of mel frequency bins
     */
    public MelSpectrogramExtractor(int sampleRate, int nFft, int hopLength, int nMels) {
        this.sampleRate = sampleRate;
        this.nFft = nFft;
        this.hopLength = hopLength;
        this.nMels = nMels;
        this.fMin = 0.0f;
        this.fMax = sampleRate / 2.0f;
        this.melFilterbank = createMelFilterbank();
        this.window = createHannWindow();

        LOG.debug("MelSpectrogramExtractor initialized: sr={}, nFft={}, hop={}, mels={}",
            sampleRate, nFft, hopLength, nMels);
    }

    /**
     * Extracts mel spectrogram from audio samples.
     *
     * @param audio normalized audio samples [-1, 1]
     * @return mel spectrogram [n_mels, time_frames]
     */
    public float[][] extract(float[] audio) {
        // Pad audio to ensure we have enough samples
        int padLength = nFft / 2;
        float[] padded = new float[audio.length + 2 * padLength];
        System.arraycopy(audio, 0, padded, padLength, audio.length);

        // Calculate number of frames
        int numFrames = (padded.length - nFft) / hopLength + 1;
        if (numFrames <= 0) numFrames = 1;

        // Compute STFT magnitude
        float[][] stftMag = new float[nFft / 2 + 1][numFrames];

        for (int frame = 0; frame < numFrames; frame++) {
            int start = frame * hopLength;

            // Extract frame and apply window
            float[] windowed = new float[nFft];
            for (int i = 0; i < nFft && (start + i) < padded.length; i++) {
                windowed[i] = padded[start + i] * window[i];
            }

            // Compute FFT magnitude
            float[] fftMag = computeFFTMagnitude(windowed);

            // Store in STFT matrix
            for (int i = 0; i < fftMag.length; i++) {
                stftMag[i][frame] = fftMag[i];
            }
        }

        // Apply mel filterbank
        float[][] melSpec = new float[nMels][numFrames];

        for (int frame = 0; frame < numFrames; frame++) {
            for (int mel = 0; mel < nMels; mel++) {
                float sum = 0;
                for (int freq = 0; freq < melFilterbank[mel].length; freq++) {
                    sum += melFilterbank[mel][freq] * stftMag[freq][frame];
                }
                // Convert to log scale with floor
                melSpec[mel][frame] = (float) Math.log(Math.max(sum, 1e-10));
            }
        }

        // Normalize (Whisper-style)
        normalizeLogMel(melSpec);

        return melSpec;
    }

    /**
     * Computes FFT magnitude using a simple DFT implementation.
     * For production, consider using a proper FFT library.
     */
    private float[] computeFFTMagnitude(float[] signal) {
        int n = signal.length;
        int numBins = n / 2 + 1;
        float[] magnitude = new float[numBins];

        for (int k = 0; k < numBins; k++) {
            float real = 0;
            float imag = 0;

            for (int t = 0; t < n; t++) {
                double angle = -2.0 * Math.PI * k * t / n;
                real += signal[t] * Math.cos(angle);
                imag += signal[t] * Math.sin(angle);
            }

            magnitude[k] = (float) Math.sqrt(real * real + imag * imag);
        }

        return magnitude;
    }

    /**
     * Creates a Hann window function.
     */
    private float[] createHannWindow() {
        float[] win = new float[nFft];
        for (int i = 0; i < nFft; i++) {
            win[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (nFft - 1))));
        }
        return win;
    }

    /**
     * Creates mel filterbank matrix.
     */
    private float[][] createMelFilterbank() {
        int numBins = nFft / 2 + 1;
        float[][] filterbank = new float[nMels][numBins];

        // Convert frequency range to mel scale
        float melMin = hzToMel(fMin);
        float melMax = hzToMel(fMax);

        // Create mel points
        float[] melPoints = new float[nMels + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = melMin + (melMax - melMin) * i / (melPoints.length - 1);
        }

        // Convert mel points to Hz and then to FFT bin indices
        float[] hzPoints = new float[melPoints.length];
        int[] binPoints = new int[melPoints.length];
        for (int i = 0; i < melPoints.length; i++) {
            hzPoints[i] = melToHz(melPoints[i]);
            binPoints[i] = (int) Math.floor((nFft + 1) * hzPoints[i] / sampleRate);
        }

        // Create triangular filters
        for (int mel = 0; mel < nMels; mel++) {
            int startBin = binPoints[mel];
            int centerBin = binPoints[mel + 1];
            int endBin = binPoints[mel + 2];

            // Rising slope
            for (int bin = startBin; bin < centerBin && bin < numBins; bin++) {
                if (centerBin != startBin) {
                    filterbank[mel][bin] = (float) (bin - startBin) / (centerBin - startBin);
                }
            }

            // Falling slope
            for (int bin = centerBin; bin < endBin && bin < numBins; bin++) {
                if (endBin != centerBin) {
                    filterbank[mel][bin] = (float) (endBin - bin) / (endBin - centerBin);
                }
            }
        }

        return filterbank;
    }

    /**
     * Converts frequency in Hz to mel scale.
     */
    private float hzToMel(float hz) {
        return (float) (2595.0 * Math.log10(1.0 + hz / 700.0));
    }

    /**
     * Converts mel scale to frequency in Hz.
     */
    private float melToHz(float mel) {
        return (float) (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0));
    }

    /**
     * Normalizes log mel spectrogram (Whisper-style).
     */
    private void normalizeLogMel(float[][] melSpec) {
        // Find max value
        float maxVal = Float.NEGATIVE_INFINITY;
        for (float[] row : melSpec) {
            for (float val : row) {
                if (val > maxVal) maxVal = val;
            }
        }

        // Clamp to max - 8.0 and normalize to [0, 1]
        float minVal = maxVal - 8.0f;
        for (int i = 0; i < melSpec.length; i++) {
            for (int j = 0; j < melSpec[i].length; j++) {
                melSpec[i][j] = Math.max(melSpec[i][j], minVal);
                melSpec[i][j] = (melSpec[i][j] - minVal) / 8.0f;
            }
        }
    }

    /**
     * Gets the expected frame count for a given audio length.
     *
     * @param audioSamples number of audio samples
     * @return number of spectrogram frames
     */
    public int getFrameCount(int audioSamples) {
        int padded = audioSamples + nFft;
        return (padded - nFft) / hopLength + 1;
    }

    /**
     * Gets the time in seconds for a given frame index.
     *
     * @param frameIndex the frame index
     * @return time in seconds
     */
    public float frameToTime(int frameIndex) {
        return (float) frameIndex * hopLength / sampleRate;
    }

    /**
     * Gets the frame index for a given time in seconds.
     *
     * @param timeSeconds time in seconds
     * @return frame index
     */
    public int timeToFrame(float timeSeconds) {
        return (int) (timeSeconds * sampleRate / hopLength);
    }
}
