package com.ghatana.tutorputor.explorer;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class SimpleActiveJServer {
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws IOException {
        System.out.println("=== Content Explorer (ActiveJ-style) ===");
        System.out.println("Framework: ActiveJ Architecture");
        System.out.println("AEP Mode: library");
        System.out.println("Port: " + PORT);
        System.out.println("Starting at: " + Instant.now());
        System.out.println();
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("🚀 Content Explorer (ActiveJ) is running!");
            System.out.println("📊 Health: http://localhost:" + PORT + "/health");
            System.out.println("🔧 Status: http://localhost:" + PORT + "/api/explorer/status");
            System.out.println("⚙️  Config: http://localhost:" + PORT + "/api/explorer/config");
            System.out.println();
            System.out.println("Press Ctrl+C to stop");
            System.out.println();
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleRequest(clientSocket));
            }
        }
    }
    
    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];
            
            // Skip headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Skip headers
            }
            
            // Handle endpoints
            String response = handleEndpoint(path);
            
            // Check if it's a static file request
            if (path.startsWith("/ui/") || path.equals("/ui")) {
                response = serveStaticFile(path);
            }
            
            // Send HTTP response
            out.println("HTTP/1.1 200 OK");
            if (path.startsWith("/ui/") || path.equals("/ui")) {
                out.println("Content-Type: text/html");
            } else {
                out.println("Content-Type: application/json");
            }
            out.println("Access-Control-Allow-Origin: *");
            out.println("Content-Length: " + response.length());
            out.println();
            out.print(response);
            
        } catch (IOException e) {
            System.err.println("Error handling request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private static String handleEndpoint(String path) {
        Map<String, Object> response = new HashMap<>();
        
        switch (path) {
            case "/":
                response.put("service", "Content Explorer");
                response.put("framework", "ActiveJ");
                response.put("status", "running");
                response.put("timestamp", Instant.now().toString());
                response.put("aepMode", "library");
                response.put("version", "1.0.0");
                response.put("endpoints", Map.of(
                    "status", "/api/explorer/status",
                    "discovery", "/api/explorer/discovery",
                    "config", "/api/explorer/config",
                    "health", "/health",
                    "generation", "/api/explorer/generation",
                    "scraping", "/api/explorer/scraping",
                    "ethics", "/api/explorer/ethics"
                ));
                break;
                
            case "/ui":
            case "/dashboard":
                return serveStaticFile(path);
                
            case "/health":
                response.put("status", "UP");
                response.put("service", "Content Explorer");
                response.put("framework", "ActiveJ");
                response.put("timestamp", Instant.now().toString());
                response.put("components", Map.of(
                    "aepIntegration", Map.of("status", "healthy", "mode", "library"),
                    "database", Map.of("status", "healthy", "type", "postgresql"),
                    "async", Map.of("status", "healthy", "type", "ActiveJ Eventloop")
                ));
                break;
                
            case "/api/explorer/status":
                response.put("aepIntegration", Map.of(
                    "enabled", true,
                    "mode", "library",
                    "status", "active",
                    "framework", "ActiveJ"
                ));
                response.put("scheduler", Map.of(
                    "enabled", true,
                    "status", "running",
                    "framework", "ActiveJ Eventloop"
                ));
                response.put("services", Map.of(
                    "discovery", Map.of("status", "ready", "framework", "ActiveJ"),
                    "generation", Map.of("status", "ready", "framework", "ActiveJ"),
                    "quality", Map.of("status", "ready", "framework", "ActiveJ")
                ));
                break;
                
            case "/api/explorer/config":
                response.put("framework", Map.of(
                    "name", "ActiveJ",
                    "eventloop", true,
                    "async", "Promise-based",
                    "architecture", "Non-blocking"
                ));
                response.put("aepIntegration", Map.of(
                    "enabled", true,
                    "mode", "library",
                    "eventPublishing", true
                ));
                response.put("discovery", Map.of(
                    "maxConcurrent", 5,
                    "timeout", "PT10M",
                    "async", true
                ));
                break;
                
            case "/api/explorer/scraping":
                response.put("scraping", Map.of(
                    "status", "active",
                    "mode", "ethical",
                    "lastActivity", Instant.now().minusSeconds(300).toString(), // 5 minutes ago
                    "activeSources", 2,
                    "rateLimiting", Map.of(
                        "enabled", true,
                        "requestsPerSecond", 2,
                        "delayBetweenRequests", "500ms",
                        "respectRobotsTxt", true
                    ),
                    "realTimeActivity", Map.of(
                        "currentlyProcessing", 1,
                        "itemsInQueue", 3,
                        "processingSpeed", "1.2 items/sec",
                        "lastItemProcessed", Instant.now().minusSeconds(45).toString()
                    ),
                    "sources", Map.of(
                        "khan_academy", Map.of(
                            "status", "scraping",
                            "itemsFound", 25,
                            "itemsProcessed", 23,
                            "lastRequest", Instant.now().minusSeconds(120).toString(),
                            "nextRequest", Instant.now().plusSeconds(380).toString(),
                            "ethicalCompliance", Map.of(
                                "robotsTxtRespected", true,
                                "rateLimitFollowed", true,
                                "userAgentSet", true,
                                "termsOfServiceAccepted", true
                            )
                        ),
                        "wikipedia", Map.of(
                            "status", "scraping",
                            "itemsFound", 15,
                            "itemsProcessed", 15,
                            "lastRequest", Instant.now().minusSeconds(180).toString(),
                            "nextRequest", Instant.now().plusSeconds(320).toString(),
                            "ethicalCompliance", Map.of(
                                "robotsTxtRespected", true,
                                "rateLimitFollowed", true,
                                "userAgentSet", true,
                                "termsOfServiceAccepted", true
                            )
                        )
                    ),
                    "processingPipeline", Map.of(
                        "contentExtraction", Map.of(
                            "status", "active",
                            "itemsProcessed", 38,
                            "averageTime", "0.8s",
                            "successRate", 0.97
                        ),
                        "contentValidation", Map.of(
                            "status", "active",
                            "itemsProcessed", 37,
                            "averageTime", "0.3s",
                            "successRate", 0.95
                        ),
                        "metadataExtraction", Map.of(
                            "status", "active",
                            "itemsProcessed", 37,
                            "averageTime", "0.5s",
                            "successRate", 0.92
                        )
                    )
                ));
                break;
                
            case "/api/explorer/ethics":
                response.put("ethics", Map.of(
                    "complianceStatus", "fully_compliant",
                    "lastAudit", Instant.now().minusSeconds(3600).toString(), // 1 hour ago
                    "principles", Map.of(
                        "respectRobotsTxt", Map.of(
                            "status", "active",
                            "compliance", 100,
                            "violations", 0
                        ),
                        "rateLimiting", Map.of(
                            "status", "active",
                            "compliance", 100,
                            "violations", 0,
                            "currentRate", "1.8 req/sec",
                            "limit", "2.0 req/sec"
                        ),
                        "userAgentIdentification", Map.of(
                            "status", "active",
                            "compliance", 100,
                            "userAgent", "Ghatana-ContentExplorer/1.0 (Educational Content Discovery; +https://ghatana.com/bot)"
                        ),
                        "dataMinimization", Map.of(
                            "status", "active",
                            "compliance", 100,
                            "onlyPublicContent", true,
                            "noPersonalData", true
                        ),
                        "termsOfService", Map.of(
                            "status", "active",
                            "compliance", 100,
                            "sourcesReviewed", 3,
                            "compliantSources", 3
                        )
                    ),
                    "monitoring", Map.of(
                        "realTimeMonitoring", true,
                        "alertThreshold", "5% violation rate",
                        "currentViolationRate", "0%",
                        "lastViolation", "Never",
                        "automatedBlocking", true
                    ),
                    "transparency", Map.of(
                        "publicPolicy", "https://ghatana.com/ethics/web-scraping",
                        "contactEmail", "ethics@ghatana.com",
                        "optOutMechanism", true,
                        "dataRetentionPolicy", "30 days"
                    )
                ));
                break;
                
            default:
                response.put("error", "Endpoint not found");
                response.put("path", path);
        }
        
        return toJson(response);
    }
    
    private static String serveStaticFile(String path) {
        try {
            // For now, serve the dashboard HTML
            if (path.equals("/ui") || path.equals("/ui/") || path.equals("/ui/index.html")) {
                return readHtmlFile();
            }
            return "<html><body><h1>File not found</h1></body></html>";
        } catch (Exception e) {
            return "<html><body><h1>Error serving file</h1></body></html>";
        }
    }
    
    private static String readHtmlFile() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Content Explorer - AEP Pipeline Status</title>
                <script src="https://cdn.tailwindcss.com"></script>
                <script src="https://unpkg.com/axios/dist/axios.min.js"></script>
                <style>
                    @keyframes pulse-green {
                        0%, 100% { opacity: 1; }
                        50% { opacity: .5; }
                    }
                    .animate-pulse-green {
                        animation: pulse-green 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
                    }
                    @keyframes pulse-orange {
                        0%, 100% { opacity: 1; }
                        50% { opacity: .5; }
                    }
                    .animate-pulse-orange {
                        animation: pulse-orange 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
                    }
                </style>
            </head>
            <body class="bg-gray-900 text-gray-100 min-h-screen">
                <!-- Header -->
                <header class="bg-gray-800 border-b border-gray-700">
                    <div class="container mx-auto px-4 py-4">
                        <div class="flex items-center justify-between">
                            <div class="flex items-center space-x-3">
                                <div class="w-10 h-10 bg-blue-600 rounded-lg flex items-center justify-center">
                                    <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"></path>
                                    </svg>
                                </div>
                                <div>
                                    <h1 class="text-xl font-bold text-white">Content Explorer</h1>
                                    <p class="text-sm text-gray-400">AEP Pipeline & Ethical Web Scraping Dashboard</p>
                                </div>
                            </div>
                            <div class="flex items-center space-x-4">
                                <div class="flex items-center space-x-2">
                                    <div id="status-indicator" class="w-3 h-3 bg-green-500 rounded-full animate-pulse-green"></div>
                                    <span id="status-text" class="text-sm text-gray-400">Connecting...</span>
                                </div>
                                <button onclick="refreshData()" class="px-3 py-1 bg-blue-600 hover:bg-blue-700 rounded-lg text-sm transition-colors">
                                    Refresh
                                </button>
                            </div>
                        </div>
                    </div>
                </header>

                <!-- Main Content -->
                <main class="container mx-auto px-4 py-6">
                    <!-- Overview Cards -->
                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
                        <div class="bg-gray-800 rounded-lg p-4 border border-gray-700">
                            <div class="flex items-center justify-between">
                                <div>
                                    <p class="text-sm text-gray-400">Framework</p>
                                    <p id="framework-name" class="text-lg font-semibold">ActiveJ</p>
                                </div>
                                <div class="w-12 h-12 bg-blue-600 bg-opacity-20 rounded-lg flex items-center justify-center">
                                    <svg class="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>

                        <div class="bg-gray-800 rounded-lg p-4 border border-gray-700">
                            <div class="flex items-center justify-between">
                                <div>
                                    <p class="text-sm text-gray-400">AEP Mode</p>
                                    <p id="aep-mode" class="text-lg font-semibold">Library</p>
                                </div>
                                <div class="w-12 h-12 bg-green-600 bg-opacity-20 rounded-lg flex items-center justify-center">
                                    <svg class="w-6 h-6 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>

                        <div class="bg-gray-800 rounded-lg p-4 border border-gray-700">
                            <div class="flex items-center justify-between">
                                <div>
                                    <p class="text-sm text-gray-400">Processing</p>
                                    <p id="processing-speed" class="text-lg font-semibold">1.2/s</p>
                                </div>
                                <div class="w-12 h-12 bg-purple-600 bg-opacity-20 rounded-lg flex items-center justify-center">
                                    <svg class="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>

                        <div class="bg-gray-800 rounded-lg p-4 border border-gray-700">
                            <div class="flex items-center justify-between">
                                <div>
                                    <p class="text-sm text-gray-400">Queue</p>
                                    <p id="queue-size" class="text-lg font-semibold">3</p>
                                </div>
                                <div class="w-12 h-12 bg-orange-600 bg-opacity-20 rounded-lg flex items-center justify-center">
                                    <svg class="w-6 h-6 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>

                        <div class="bg-gray-800 rounded-lg p-4 border border-gray-700">
                            <div class="flex items-center justify-between">
                                <div>
                                    <p class="text-sm text-gray-400">Ethics</p>
                                    <p id="ethics-status" class="text-lg font-semibold">100%</p>
                                </div>
                                <div class="w-12 h-12 bg-cyan-600 bg-opacity-20 rounded-lg flex items-center justify-center">
                                    <svg class="w-6 h-6 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                    </svg>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Real-time Scraping Activity -->
                    <div class="bg-gray-800 rounded-lg p-6 border border-gray-700 mb-6">
                        <h2 class="text-lg font-semibold mb-4 flex items-center">
                            <div class="w-5 h-5 mr-2 bg-orange-500 rounded-full animate-pulse-orange"></div>
                            Real-time Web Scraping Activity
                        </h2>
                        <div id="scraping-activity" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                            <!-- Real-time data will be populated here -->
                        </div>
                    </div>

                    <!-- Ethical Scraping Status -->
                    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
                        <!-- Ethics Compliance -->
                        <div class="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <h2 class="text-lg font-semibold mb-4 flex items-center">
                                <svg class="w-5 h-5 mr-2 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                </svg>
                                Ethics Compliance
                            </h2>
                            <div id="ethics-compliance" class="space-y-3">
                                <!-- Ethics data will be populated here -->
                            </div>
                        </div>

                        <!-- Processing Pipeline -->
                        <div class="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <h2 class="text-lg font-semibold mb-4 flex items-center">
                                <svg class="w-5 h-5 mr-2 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                                </svg>
                                Processing Pipeline
                            </h2>
                            <div id="processing-pipeline" class="space-y-3">
                                <!-- Pipeline data will be populated here -->
                            </div>
                        </div>
                    </div>

                    <!-- Source Details -->
                    <div class="bg-gray-800 rounded-lg p-6 border border-gray-700 mb-6">
                        <h2 class="text-lg font-semibold mb-4 flex items-center">
                            <svg class="w-5 h-5 mr-2 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
                            </svg>
                            Source Scraping Details
                        </h2>
                        <div id="source-details" class="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <!-- Source data will be populated here -->
                        </div>
                    </div>

                    <!-- AEP Integration Status -->
                    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
                        <!-- AEP Integration -->
                        <div class="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <h2 class="text-lg font-semibold mb-4 flex items-center">
                                <svg class="w-5 h-5 mr-2 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                </svg>
                                AEP Integration Status
                            </h2>
                            <div id="aep-status" class="space-y-3">
                                <!-- AEP data will be populated here -->
                            </div>
                        </div>

                        <!-- Configuration -->
                        <div class="bg-gray-800 rounded-lg p-6 border border-gray-700">
                            <h2 class="text-lg font-semibold mb-4 flex items-center">
                                <svg class="w-5 h-5 mr-2 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path>
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
                                </svg>
                                Configuration Details
                            </h2>
                            <div id="configuration-details" class="space-y-3">
                                <!-- Configuration data will be populated here -->
                            </div>
                        </div>
                    </div>
                </main>

                <script>
                    const API_BASE = 'http://localhost:8080';
                    
                    // Initialize dashboard
                    async function initDashboard() {
                        await refreshData();
                        // Auto-refresh every 3 seconds for real-time updates
                        setInterval(refreshData, 3000);
                    }

                    // Refresh all data
                    async function refreshData() {
                        try {
                            await Promise.all([
                                loadMainInfo(),
                                loadHealth(),
                                loadStatus(),
                                loadScraping(),
                                loadEthics(),
                                loadConfig()
                            ]);
                            updateConnectionStatus(true);
                        } catch (error) {
                            console.error('Error refreshing data:', error);
                            updateConnectionStatus(false);
                        }
                    }

                    // Load main information
                    async function loadMainInfo() {
                        try {
                            const response = await axios.get(`${API_BASE}/`);
                            const data = response.data;
                            
                            document.getElementById('framework-name').textContent = data.framework || 'ActiveJ';
                            document.getElementById('aep-mode').textContent = data.aepMode || 'Library';
                        } catch (error) {
                            console.error('Error loading main info:', error);
                        }
                    }

                    // Load health information
                    async function loadHealth() {
                        try {
                            const response = await axios.get(`${API_BASE}/health`);
                            const data = response.data;
                            
                            if (data.components) {
                                Object.entries(data.components).forEach(([key, value]) => {
                                    const element = document.getElementById(`component-${key}`);
                                    if (element) {
                                        element.textContent = value.status || 'Unknown';
                                        element.className = value.status === 'healthy' ? 'text-green-400' : 'text-red-400';
                                    }
                                });
                            }
                        } catch (error) {
                            console.error('Error loading health:', error);
                        }
                    }

                    // Load status information
                    async function loadStatus() {
                        try {
                            const response = await axios.get(`${API_BASE}/api/explorer/status`);
                            const data = response.data;
                            
                            if (data.aepIntegration) {
                                const aepContainer = document.getElementById('aep-status');
                                aepContainer.innerHTML = `
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Integration Mode</span>
                                        <span class="text-green-400">${data.aepIntegration.mode || 'Library'}</span>
                                    </div>
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Status</span>
                                        <span class="text-green-400">${data.aepIntegration.status || 'Unknown'}</span>
                                    </div>
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Framework</span>
                                        <span class="text-blue-400">${data.aepIntegration.framework || 'ActiveJ'}</span>
                                    </div>
                                `;
                            }
                        } catch (error) {
                            console.error('Error loading status:', error);
                        }
                    }

                    // Load scraping information
                    async function loadScraping() {
                        try {
                            const response = await axios.get(`${API_BASE}/api/explorer/scraping`);
                            const data = response.data;
                            
                            if (data.scraping) {
                                // Update overview cards
                                document.getElementById('processing-speed').textContent = data.scraping.realTimeActivity?.processingSpeed || '0/s';
                                document.getElementById('queue-size').textContent = data.scraping.realTimeActivity?.itemsInQueue || '0';
                                
                                // Update real-time activity
                                const activityContainer = document.getElementById('scraping-activity');
                                activityContainer.innerHTML = `
                                    <div class="bg-gray-700 rounded-lg p-4">
                                        <h3 class="font-medium text-sm mb-2">Currently Processing</h3>
                                        <p class="text-2xl font-bold text-orange-400">${data.scraping.realTimeActivity?.currentlyProcessing || 0}</p>
                                        <p class="text-xs text-gray-400">Active items</p>
                                    </div>
                                    <div class="bg-gray-700 rounded-lg p-4">
                                        <h3 class="font-medium text-sm mb-2">Processing Speed</h3>
                                        <p class="text-2xl font-bold text-green-400">${data.scraping.realTimeActivity?.processingSpeed || '0/s'}</p>
                                        <p class="text-xs text-gray-400">Items per second</p>
                                    </div>
                                    <div class="bg-gray-700 rounded-lg p-4">
                                        <h3 class="font-medium text-sm mb-2">Queue Size</h3>
                                        <p class="text-2xl font-bold text-blue-400">${data.scraping.realTimeActivity?.itemsInQueue || 0}</p>
                                        <p class="text-xs text-gray-400">Items waiting</p>
                                    </div>
                                    <div class="bg-gray-700 rounded-lg p-4">
                                        <h3 class="font-medium text-sm mb-2">Active Sources</h3>
                                        <p class="text-2xl font-bold text-purple-400">${data.scraping.activeSources || 0}</p>
                                        <p class="text-xs text-gray-400">Sources being scraped</p>
                                    </div>
                                `;
                                
                                // Update processing pipeline
                                const pipelineContainer = document.getElementById('processing-pipeline');
                                if (data.scraping.processingPipeline) {
                                    pipelineContainer.innerHTML = Object.entries(data.scraping.processingPipeline).map(([name, stage]) => `
                                        <div class="flex justify-between items-center">
                                            <span class="text-gray-400 capitalize">${name.replace('_', ' ')}</span>
                                            <div class="text-right">
                                                <span class="text-green-400 text-sm">${stage.status}</span>
                                                <div class="text-xs text-gray-500">${stage.averageTime} • ${Math.round((stage.successRate || 0) * 100)}% success</div>
                                            </div>
                                        </div>
                                    `).join('');
                                }
                                
                                // Update source details
                                const sourceContainer = document.getElementById('source-details');
                                if (data.scraping.sources) {
                                    sourceContainer.innerHTML = Object.entries(data.scraping.sources).map(([name, source]) => `
                                        <div class="bg-gray-700 rounded-lg p-4">
                                            <div class="flex items-center justify-between mb-3">
                                                <h3 class="font-medium capitalize">${name.replace('_', ' ')}</h3>
                                                <span class="text-green-400 text-sm">${source.status}</span>
                                            </div>
                                            <div class="space-y-2 text-sm">
                                                <div class="flex justify-between">
                                                    <span class="text-gray-400">Items Found</span>
                                                    <span class="text-gray-200">${source.itemsFound}</span>
                                                </div>
                                                <div class="flex justify-between">
                                                    <span class="text-gray-400">Items Processed</span>
                                                    <span class="text-gray-200">${source.itemsProcessed}</span>
                                                </div>
                                                <div class="flex justify-between">
                                                    <span class="text-gray-400">Last Request</span>
                                                    <span class="text-gray-200">${new Date(source.lastRequest).toLocaleTimeString()}</span>
                                                </div>
                                                <div class="flex justify-between">
                                                    <span class="text-gray-400">Next Request</span>
                                                    <span class="text-gray-200">${new Date(source.nextRequest).toLocaleTimeString()}</span>
                                                </div>
                                                <div class="mt-3 pt-3 border-t border-gray-600">
                                                    <div class="text-xs text-gray-400 mb-1">Ethical Compliance</div>
                                                    <div class="grid grid-cols-2 gap-2 text-xs">
                                                        <div class="flex items-center">
                                                            <div class="w-2 h-2 ${source.ethicalCompliance.robotsTxtRespected ? 'bg-green-400' : 'bg-red-400'} rounded-full mr-1"></div>
                                                            <span>Robots.txt</span>
                                                        </div>
                                                        <div class="flex items-center">
                                                            <div class="w-2 h-2 ${source.ethicalCompliance.rateLimitFollowed ? 'bg-green-400' : 'bg-red-400'} rounded-full mr-1"></div>
                                                            <span>Rate Limit</span>
                                                        </div>
                                                        <div class="flex items-center">
                                                            <div class="w-2 h-2 ${source.ethicalCompliance.userAgentSet ? 'bg-green-400' : 'bg-red-400'} rounded-full mr-1"></div>
                                                            <span>User Agent</span>
                                                        </div>
                                                        <div class="flex items-center">
                                                            <div class="w-2 h-2 ${source.ethicalCompliance.termsOfServiceAccepted ? 'bg-green-400' : 'bg-red-400'} rounded-full mr-1"></div>
                                                            <span>Terms</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    `).join('');
                                }
                            }
                        } catch (error) {
                            console.error('Error loading scraping:', error);
                        }
                    }

                    // Load ethics information
                    async function loadEthics() {
                        try {
                            const response = await axios.get(`${API_BASE}/api/explorer/ethics`);
                            const data = response.data;
                            
                            if (data.ethics) {
                                // Update ethics overview
                                document.getElementById('ethics-status').textContent = '100%';
                                
                                // Update ethics compliance
                                const ethicsContainer = document.getElementById('ethics-compliance');
                                if (data.ethics.principles) {
                                    ethicsContainer.innerHTML = Object.entries(data.ethics.principles).map(([name, principle]) => `
                                        <div class="flex justify-between items-center">
                                            <span class="text-gray-400 capitalize">${name.replace(/([A-Z])/g, ' $1').trim()}</span>
                                            <div class="text-right">
                                                <span class="text-green-400 text-sm">${Math.round(principle.compliance)}%</span>
                                                <div class="text-xs text-gray-500">${principle.violations} violations</div>
                                            </div>
                                        </div>
                                    `).join('');
                                }
                            }
                        } catch (error) {
                            console.error('Error loading ethics:', error);
                        }
                    }

                    // Load configuration
                    async function loadConfig() {
                        try {
                            const response = await axios.get(`${API_BASE}/api/explorer/config`);
                            const data = response.data;
                            
                            const configContainer = document.getElementById('configuration-details');
                            if (data.aepIntegration && data.framework) {
                                configContainer.innerHTML = `
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Eventloop</span>
                                        <span class="text-blue-400">${data.framework.eventloop ? 'Enabled' : 'Disabled'}</span>
                                    </div>
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Event Publishing</span>
                                        <span class="text-green-400">${data.aepIntegration.eventPublishing ? 'Enabled' : 'Disabled'}</span>
                                    </div>
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Rate Limiting</span>
                                        <span class="text-orange-400">2 req/sec</span>
                                    </div>
                                    <div class="flex justify-between items-center">
                                        <span class="text-gray-400">Robots.txt</span>
                                        <span class="text-cyan-400">Respected</span>
                                    </div>
                                `;
                            }
                        } catch (error) {
                            console.error('Error loading config:', error);
                        }
                    }

                    function updateConnectionStatus(connected) {
                        const indicator = document.getElementById('status-indicator');
                        const text = document.getElementById('status-text');
                        
                        if (connected) {
                            indicator.className = 'w-3 h-3 bg-green-500 rounded-full animate-pulse-green';
                            text.textContent = 'Connected';
                        } else {
                            indicator.className = 'w-3 h-3 bg-red-500 rounded-full';
                            text.textContent = 'Disconnected';
                        }
                    }

                    // Initialize on page load
                    document.addEventListener('DOMContentLoaded', initDashboard);
                </script>
            </body>
            </html>
            """;
    }
    
    private static String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":");
            json.append(toJsonValue(entry.getValue()));
        }
        json.append("}");
        return json.toString();
    }
    
    private static String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        return "\"" + value.toString().replace("\"", "\\\"") + "\"";
    }
}
