/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages user presence tracking for real-time collaboration.
 *
 * @doc.type class
 * @doc.purpose Track online users and their presence status
 * @doc.layer infrastructure
 * @doc.pattern Manager
 */
@Singleton
public class PresenceManager {
    private static final Logger logger = LoggerFactory.getLogger(PresenceManager.class);
    
    // Map<TenantId, Map<UserId, PresenceInfo>>
    private final Map<String, Map<String, PresenceInfo>> presenceByTenant = new ConcurrentHashMap<>();
    
    // Map<SessionId, PresenceInfo> for quick session lookup
    private final Map<String, PresenceInfo> presenceBySession = new ConcurrentHashMap<>();
    
    private final ConnectionManager connectionManager;
    
    @Inject
    public PresenceManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }
    
    /**
     * Update user presence when they connect.
     */
    public void userConnected(String tenantId, String userId, String sessionId, Map<String, Object> metadata) {
        PresenceInfo presence = new PresenceInfo(userId, sessionId, "online", metadata);
        
        presenceByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
            .put(userId, presence);
        presenceBySession.put(sessionId, presence);
        
        logger.info("User {} connected to tenant {} (session: {})", userId, tenantId, sessionId);
        
        // Broadcast presence update
        broadcastPresenceUpdate(tenantId, userId, "online", metadata);
    }
    
    /**
     * Update user presence when they disconnect.
     */
    public void userDisconnected(String tenantId, String userId, String sessionId) {
        Map<String, PresenceInfo> tenantPresence = presenceByTenant.get(tenantId);
        if (tenantPresence != null) {
            tenantPresence.remove(userId);
            if (tenantPresence.isEmpty()) {
                presenceByTenant.remove(tenantId);
            }
        }
        
        presenceBySession.remove(sessionId);
        
        logger.info("User {} disconnected from tenant {} (session: {})", userId, tenantId, sessionId);
        
        // Broadcast presence update
        broadcastPresenceUpdate(tenantId, userId, "offline", null);
    }
    
    /**
     * Update user status (online, away, busy, etc.).
     */
    public void updateStatus(String tenantId, String userId, String status, Map<String, Object> metadata) {
        Map<String, PresenceInfo> tenantPresence = presenceByTenant.get(tenantId);
        if (tenantPresence != null) {
            PresenceInfo presence = tenantPresence.get(userId);
            if (presence != null) {
                presence.status = status;
                presence.metadata = metadata;
                presence.lastUpdated = System.currentTimeMillis();
                
                broadcastPresenceUpdate(tenantId, userId, status, metadata);
            }
        }
    }
    
    /**
     * Update user cursor position (for canvas collaboration).
     */
    public void updateCursor(String tenantId, String userId, String sessionId, Map<String, Object> cursor) {
        Map<String, PresenceInfo> tenantPresence = presenceByTenant.get(tenantId);
        if (tenantPresence != null) {
            PresenceInfo presence = tenantPresence.get(userId);
            if (presence != null && sessionId.equals(presence.sessionId)) {
                presence.cursor = cursor;
                presence.lastUpdated = System.currentTimeMillis();
                
                // Broadcast cursor update (throttled in production)
                Map<String, Object> update = Map.of(
                    "type", "presence:cursor",
                    "userId", userId,
                    "sessionId", sessionId,
                    "cursor", cursor,
                    "timestamp", System.currentTimeMillis()
                );
                connectionManager.broadcast(tenantId, update);
            }
        }
    }
    
    /**
     * Get all online users for a tenant.
     */
    public List<Map<String, Object>> getOnlineUsers(String tenantId) {
        Map<String, PresenceInfo> tenantPresence = presenceByTenant.get(tenantId);
        if (tenantPresence == null) {
            return Collections.emptyList();
        }
        
        return tenantPresence.values().stream()
            .map(PresenceInfo::toMap)
            .collect(Collectors.toList());
    }
    
    /**
     * Get online users for a specific session/room.
     */
    public List<Map<String, Object>> getOnlineUsersInSession(String tenantId, String sessionId) {
        Map<String, PresenceInfo> tenantPresence = presenceByTenant.get(tenantId);
        if (tenantPresence == null) {
            return Collections.emptyList();
        }
        
        return tenantPresence.values().stream()
            .filter(p -> sessionId.equals(p.metadata.get("sessionId")))
            .map(PresenceInfo::toMap)
            .collect(Collectors.toList());
    }
    
    private void broadcastPresenceUpdate(String tenantId, String userId, String status, Map<String, Object> metadata) {
        Map<String, Object> update = new HashMap<>();
        update.put("type", "presence:update");
        update.put("userId", userId);
        update.put("status", status);
        update.put("timestamp", System.currentTimeMillis());
        if (metadata != null) {
            update.put("metadata", metadata);
        }
        
        connectionManager.broadcast(tenantId, update);
    }
    
    /**
     * Internal class to track presence information.
     */
    private static class PresenceInfo {
        String userId;
        String sessionId;
        String status;
        Map<String, Object> metadata;
        Map<String, Object> cursor;
        long lastUpdated;
        
        PresenceInfo(String userId, String sessionId, String status, Map<String, Object> metadata) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.status = status;
            this.metadata = metadata != null ? metadata : new HashMap<>();
            this.lastUpdated = System.currentTimeMillis();
        }
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", userId);
            map.put("sessionId", sessionId);
            map.put("status", status);
            map.put("metadata", metadata);
            if (cursor != null) {
                map.put("cursor", cursor);
            }
            map.put("lastUpdated", lastUpdated);
            return map;
        }
    }
}
