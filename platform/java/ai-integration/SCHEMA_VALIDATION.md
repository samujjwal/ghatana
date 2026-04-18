# LLM Structured Output Schema Validation

## Overview

The `LLMService` interface now provides type-safe structured output generation with schema validation through the `generateStructured(String prompt, Class<T> schemaClass)` method.

## Implementation

### LLMService Interface

```java
/**
 * Generates a structural response based on a prompt with schema validation.
 * The LLM response is parsed and validated against the provided schema class using Jackson.
 * @param prompt The input prompt.
 * @param schemaClass The Java class to parse and validate the response against.
 * @param <T> The type of the structured output.
 * @return A Promise resolving to the parsed and validated structured output.
 * @throws RuntimeException if the LLM response is invalid JSON or does not match the schema.
 */
<T> Promise<T> generateStructured(String prompt, Class<T> schemaClass);
```

### OpenAIService Implementation

The `OpenAIService` implements this method by:

1. **Requesting JSON Mode**: The service enables OpenAI's JSON mode by setting `response_format.type = "json_object"` in the API request. This guarantees the LLM returns valid JSON.

2. **Parsing with Jackson**: The JSON response is parsed using Jackson's `ObjectMapper.readValue()` method, which validates the structure against the provided schema class.

3. **Error Handling**: If parsing fails (invalid JSON, missing fields, wrong types), a `RuntimeException` is thrown with a descriptive message.

## Error Handling

### Invalid JSON Response

If the LLM returns invalid JSON (should not happen with OpenAI JSON mode enabled):

```java
try {
    MySchema result = service.generateStructured(prompt, MySchema.class).await();
} catch (RuntimeException e) {
    // Error message: "Failed to parse LLM response as MySchema: <jackson error>"
    logger.error("Schema validation failed", e);
}
```

### Missing Required Fields

If the JSON is valid but missing required fields defined in the schema class:

```java
// Schema class with required field
class MySchema {
    public String name; // Required
    public int value;   // Required
}

// LLM returns: {"name": "test"} (missing "value")
// Result: RuntimeException - Jackson cannot deserialize
```

### Wrong Field Types

If the JSON field types don't match the schema class:

```java
// Schema expects int value
class MySchema {
    public int value;
}

// LLM returns: {"value": "not a number"}
// Result: RuntimeException - Jackson type conversion fails
```

## Schema Class Requirements

For successful schema validation, the target class should:

1. **Have a no-arg constructor** (required for Jackson deserialization)
2. **Use public fields or Jackson annotations** for property access
3. **Match the JSON structure** returned by the LLM

Example schema class:

```java
public class TaskSchema {
    public String title;
    public String description;
    public int priority;
    public boolean completed;
    
    public TaskSchema() {}
    
    public TaskSchema(String title, String description, int priority, boolean completed) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.completed = completed;
    }
}
```

### Using Jackson Annotations

For more control, use Jackson annotations:

```java
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

public class TaskSchema {
    private final String title;
    private final int priority;
    
    @JsonCreator
    public TaskSchema(
        @JsonProperty("title") String title,
        @JsonProperty("priority") int priority
    ) {
        this.title = title;
        this.priority = priority;
    }
    
    public String getTitle() { return title; }
    public int getPriority() { return priority; }
}
```

## Usage Example

```java
LLMService service = new OpenAIService(System.getenv("OPENAI_API_KEY"));

String prompt = "Generate a task with title='Fix bug', priority=1, completed=false";

TaskSchema task = service.generateStructured(prompt, TaskSchema.class).await();

// task is now a type-safe TaskSchema object
System.out.println("Task: " + task.title);
System.out.println("Priority: " + task.priority);
```

## Backward Compatibility

The old signature `generateStructured(String prompt)` is deprecated but still available for backward compatibility. It delegates to `generate(prompt)` and returns the raw JSON string without validation.

```java
// Deprecated - returns raw JSON string
Promise<String> json = service.generateStructured(prompt);

// Recommended - returns type-safe object with validation
Promise<TaskSchema> task = service.generateStructured(prompt, TaskSchema.class);
```

## Testing

### Unit Tests

Unit tests verify schema validation catches invalid responses:
- Invalid JSON format
- Missing required fields
- Wrong field types

See `OpenAIServiceTest.java` for unit test examples.

### Integration Tests

Integration tests verify valid structured outputs are parsed correctly:
- End-to-end flow with real OpenAI API
- Successful parsing of valid JSON
- Error handling for invalid responses

See `OpenAIServiceIntegrationTest.java` for integration test examples.

To run integration tests, set the `OPENAI_API_KEY` environment variable:

```bash
export OPENAI_API_KEY=your-key-here
./gradlew :platform:java:ai-integration:test
```

## Migration Guide

### Before (String return, no validation)

```java
Promise<String> jsonPromise = service.generateStructured(prompt);
String json = jsonPromise.await();
// Manual parsing required, no type safety
MySchema obj = mapper.readValue(json, MySchema.class);
```

### After (Type-safe with validation)

```java
Promise<MySchema> schemaPromise = service.generateStructured(prompt, MySchema.class);
MySchema obj = schemaPromise.await();
// Automatic parsing and validation, type-safe
```

## Limitations

1. **OpenAI JSON Mode**: Only works with OpenAI models that support JSON mode (gpt-4o, gpt-3.5-turbo, etc.)
2. **Schema Complexity**: Complex nested schemas may require careful prompt engineering to ensure the LLM generates the correct structure
3. **Error Messages**: Jackson error messages can be verbose; consider adding custom error parsing for better UX

## Future Enhancements

- [ ] Add support for JSON Schema validation (not just class-based)
- [ ] Add retry logic for transient parsing failures
- [ ] Add support for streaming structured output
- [ ] Add schema inference from prompt for auto-generated schemas
