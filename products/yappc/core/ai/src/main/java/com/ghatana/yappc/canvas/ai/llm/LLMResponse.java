package com.ghatana.yappc.canvas.ai.llm;

/**
 * Response object from LLM operations.
 *
 * @doc.type class
 * @doc.purpose LLM response value object
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class LLMResponse {

    private final String text;
    private final int tokensUsed;
    private final String model;

    public LLMResponse(String text, int tokensUsed, String model) {
        this.text = text;
        this.tokensUsed = tokensUsed;
        this.model = model;
    }

    public String getText() { return text; }
    public int getTokensUsed() { return tokensUsed; }
    public String getModel() { return model; }
}
