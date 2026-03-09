package com.ghatana.platform.security.auth.impl;

import com.ghatana.platform.security.auth.Credentials;

import java.util.Objects;

/**
 * A flexible credentials implementation for custom authentication types.
 * 
 * <p>This class allows for custom authentication mechanisms by accepting any type of
 * authentication data as an Object. The actual authentication logic should be handled
 * by a corresponding {@link com.ghatana.security.auth.AuthenticationProvider} that
 * understands the custom credential type.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create custom credentials with a custom type and data
 * Map<String, String> authData = new HashMap<>();
 * authData.put("apiKey", "abc123");
 * authData.put("clientId", "client-456");
 * 
 * Credentials credentials = new CustomCredentials("api-key", authData);
 * 
 * // Or using the factory method
 * Credentials credentials = Credentials.of("api-key", authData);
 * }</pre>
 * 
 * @param <T> The type of the authentication data
 
 *
 * @doc.type class
 * @doc.purpose Custom credentials
 * @doc.layer core
 * @doc.pattern Component
*/
public class CustomCredentials<T> extends Credentials {
    private final T data;
    
    /**
     * Creates a new CustomCredentials instance.
     * 
     * @param type The custom credential type (e.g., "api-key", "saml", "ldap")
     * @param data The authentication data
     * @throws NullPointerException if type is null
     */
    public CustomCredentials(String type, T data) {
        super(Objects.requireNonNull(type, "Type cannot be null"));
        this.data = data; // Allow null data
    }
    
    /**
     * Gets the authentication data.
     * 
     * @return The authentication data, or null if not set
     */
    public T getData() {
        return data;
    }
    
    /**
     * Gets the authentication data cast to the specified type.
     * 
     * @param <R> The target type
     * @param type The target class
     * @return The authentication data cast to the specified type, or null if data is null
     * @throws ClassCastException if the data cannot be cast to the specified type
     */
    @SuppressWarnings("unchecked")
    public <R> R getData(Class<R> type) {
        return data != null ? type.cast(data) : null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        CustomCredentials<?> that = (CustomCredentials<?>) o;
        return Objects.equals(data, that.data);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data);
    }
    
    @Override
    public String toString() {
        return "CustomCredentials{" +
                "type='" + getType() + '\'' +
                ", data=" + (data != null ? "[PROTECTED]" : "null") +
                '}';
    }
}
