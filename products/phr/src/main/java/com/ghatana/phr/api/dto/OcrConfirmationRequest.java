package com.ghatana.phr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for OCR confirmation.
 *
 * @doc.type class
 * @doc.purpose Request DTO for OCR confirmation with validation
 * @doc.layer product
 * @doc.pattern DTO
 */
public class OcrConfirmationRequest {

    @NotBlank(message = "correctedText is required")
    @Size(min = 1, max = 10000, message = "correctedText must be 1-10000 characters")
    private String correctedText;

    @JsonProperty("correctedText")
    public String getCorrectedText() {
        return correctedText;
    }

    public void setCorrectedText(String correctedText) {
        this.correctedText = correctedText;
    }
}
