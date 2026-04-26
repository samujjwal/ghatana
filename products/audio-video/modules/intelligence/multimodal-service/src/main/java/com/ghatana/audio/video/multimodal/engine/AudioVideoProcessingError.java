package com.ghatana.audio.video.multimodal.engine;

import com.ghatana.contracts.media.v1.AudioVideoError;
import com.ghatana.contracts.media.v1.AudioVideoErrorCategory;
import com.ghatana.contracts.media.v1.AudioVideoErrorCode;
import com.ghatana.media.error.ErrorHandler;

/**
 * @doc.type record
 * @doc.purpose Standardized multimodal error envelope aligned with the shared media error taxonomy
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AudioVideoProcessingError(String code, String category, boolean retryable, String message) {

    /**
     * Maps an arbitrary throwable to a standardized audio-video processing error.
     */
    public static AudioVideoProcessingError fromThrowable(String context, Throwable throwable) {
        String message = throwable == null ? context : context + ": " + throwable.getMessage();
        boolean retryable = ErrorHandler.isRetryable(throwable);
        if (throwable instanceof IllegalArgumentException) {
            return new AudioVideoProcessingError("media.invalid_request", "validation", false, message);
        }
        if (retryable) {
            return new AudioVideoProcessingError("media.temporarily_unavailable", "runtime", true, message);
        }
        return new AudioVideoProcessingError("media.processing_failed", "runtime", false, message);
    }

    /**
     * Converts this domain error into the shared media contract payload.
     */
    public AudioVideoError toContract() {
        return AudioVideoError.newBuilder()
                .setCode(toContractCode(code))
                .setCategory(toContractCategory(category))
                .setRetryable(retryable)
                .setMessage(message)
                .build();
    }

    /**
     * Rehydrates a domain error from the shared media contract payload.
     */
    public static AudioVideoProcessingError fromContract(AudioVideoError error) {
        return new AudioVideoProcessingError(
                fromContractCode(error.getCode()),
                fromContractCategory(error.getCategory()),
                error.getRetryable(),
                error.getMessage());
    }

    private static AudioVideoErrorCode toContractCode(String code) {
        return switch (code) {
            case "media.invalid_request" -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_INVALID_REQUEST;
            case "media.input_unavailable" -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_INPUT_UNAVAILABLE;
            case "media.platform_unavailable" -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_PLATFORM_UNAVAILABLE;
            case "media.temporarily_unavailable" -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_TEMPORARILY_UNAVAILABLE;
            case "media.recording_failed" -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_RECORDING_FAILED;
            case "media.processing_failed" -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_PROCESSING_FAILED;
            default -> AudioVideoErrorCode.AUDIO_VIDEO_ERROR_CODE_UNSPECIFIED;
        };
    }

    private static String fromContractCode(AudioVideoErrorCode code) {
        return switch (code) {
            case AUDIO_VIDEO_ERROR_CODE_INVALID_REQUEST -> "media.invalid_request";
            case AUDIO_VIDEO_ERROR_CODE_INPUT_UNAVAILABLE -> "media.input_unavailable";
            case AUDIO_VIDEO_ERROR_CODE_PLATFORM_UNAVAILABLE -> "media.platform_unavailable";
            case AUDIO_VIDEO_ERROR_CODE_TEMPORARILY_UNAVAILABLE -> "media.temporarily_unavailable";
            case AUDIO_VIDEO_ERROR_CODE_RECORDING_FAILED -> "media.recording_failed";
            case AUDIO_VIDEO_ERROR_CODE_PROCESSING_FAILED,
                    AUDIO_VIDEO_ERROR_CODE_UNSPECIFIED,
                    UNRECOGNIZED -> "media.processing_failed";
        };
    }

    private static AudioVideoErrorCategory toContractCategory(String category) {
        return switch (category) {
            case "validation" -> AudioVideoErrorCategory.AUDIO_VIDEO_ERROR_CATEGORY_VALIDATION;
            case "runtime" -> AudioVideoErrorCategory.AUDIO_VIDEO_ERROR_CATEGORY_RUNTIME;
            default -> AudioVideoErrorCategory.AUDIO_VIDEO_ERROR_CATEGORY_UNSPECIFIED;
        };
    }

    private static String fromContractCategory(AudioVideoErrorCategory category) {
        return switch (category) {
            case AUDIO_VIDEO_ERROR_CATEGORY_VALIDATION -> "validation";
            case AUDIO_VIDEO_ERROR_CATEGORY_RUNTIME,
                    AUDIO_VIDEO_ERROR_CATEGORY_UNSPECIFIED,
                    UNRECOGNIZED -> "runtime";
        };
    }
}
