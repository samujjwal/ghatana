package com.ghatana.core.event.query;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a cursor for paginated event queries.
 * The cursor contains the position in the result set and can be serialized
 * to a string for use in API responses and subsequent requests.
 */
public class Cursor implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // The event ID that the cursor points to
    private final String eventId;
    
    // The timestamp of the event (for efficient time-based queries)
    private final long timestamp;
    
    // The offset within the current page of results
    private final int offset;
    
    /**
     * Creates a new Cursor instance.
     * 
     * @param eventId The ID of the event the cursor points to
     * @param timestamp The timestamp of the event (in milliseconds since epoch)
     * @param offset The offset within the current page of results
     */
    public Cursor(String eventId, long timestamp, int offset) {
        this.eventId = Objects.requireNonNull(eventId, "Event ID cannot be null");
        this.timestamp = timestamp;
        this.offset = offset;
    }
    
    /**
     * Returns the event ID that this cursor points to.
     *
     * @return The event ID as a string
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * Returns the timestamp of the event this cursor points to.
     * 
     * @return The timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Returns the offset within the current page of results.
     *
     * @return The offset within the current page
     */
    public int getOffset() {
        return offset;
    }
    
    /**
     * Creates a new cursor for the next page of results.
     * 
     * @param nextEventId The ID of the first event in the next page
     * @param nextTimestamp The timestamp of the first event in the next page
     * @return A new Cursor instance pointing to the next page
     */
    public Cursor nextPage(String nextEventId, long nextTimestamp) {
        return new Cursor(nextEventId, nextTimestamp, 0);
    }
    
    /**
     * Creates a new cursor for the previous page of results.
     * 
     * @param prevEventId The ID of the first event in the previous page
     * @param prevTimestamp The timestamp of the first event in the previous page
     * @param pageSize The size of the page
     * @return A new Cursor instance pointing to the previous page
     */
    public Cursor previousPage(String prevEventId, long prevTimestamp, int pageSize) {
        return new Cursor(prevEventId, prevTimestamp, pageSize - 1);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Cursor cursor = (Cursor) o;
        return timestamp == cursor.timestamp && 
               offset == cursor.offset && 
               eventId.equals(cursor.eventId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId, timestamp, offset);
    }
    
    @Override
    public String toString() {
        return String.format("Cursor{eventId='%s', timestamp=%d, offset=%d}", eventId, timestamp, offset);
    }
    
    /**
     * Encodes the cursor to a string for use in API responses.
     * 
     * @return A base64-encoded string representation of the cursor
     */
    public String encode() {
        // Format: eventId|timestamp|offset
        String cursorString = String.format("%s|%d|%d", eventId, timestamp, offset);
        return java.util.Base64.getUrlEncoder().encodeToString(cursorString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    
    /**
     * Decodes a cursor from a string.
     * 
     * @param encodedCursor The base64-encoded cursor string
     * @return A Cursor instance, or null if the input is null or invalid
     */
    public static Cursor decode(String encodedCursor) {
        if (encodedCursor == null || encodedCursor.isEmpty()) {
            return null;
        }
        
        try {
            String cursorString = new String(
                java.util.Base64.getUrlDecoder().decode(encodedCursor),
                java.nio.charset.StandardCharsets.UTF_8
            );
            
            String[] parts = cursorString.split("\\|");
            if (parts.length != 3) {
                return null;
            }
            
            String eventId = parts[0];
            long timestamp = Long.parseLong(parts[1]);
            int offset = Integer.parseInt(parts[2]);
            
            return new Cursor(eventId, timestamp, offset);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Creates a cursor pointing to the beginning of the result set.
     *
     * @return A new Cursor instance initialized to the start of the result set
     */
    public static Cursor initial() {
        return new Cursor("", 0, 0);
    }
}
