package com.ghatana.yappc.sdlc.performance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU detection and information utility. Detects NVIDIA GPUs and provides configuration
 * recommendations.
 *
 * <p><b>Detection Methods:</b>
 *
 * <ul>
 *   <li><b>nvidia-smi</b>: Query NVIDIA GPU driver
 *   <li><b>Environment Variables</b>: CUDA_VISIBLE_DEVICES, NVIDIA_VISIBLE_DEVICES
 *   <li><b>Docker</b>: Detect GPU availability in containers
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * GPUDetector detector = new GPUDetector();
 *
 * if (detector.isGPUAvailable()) {
 *     GPUInfo info = detector.getGPUInfo();
 *     log.info("GPU: {} with {}MB memory", info.name(), info.memoryMB());
 * } else {
 *     log.warn("No GPU detected, using CPU inference (slower)");
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GPU detection and configuration for LLM inference
 * @doc.layer platform
 * @doc.pattern Utility|GPUDetection
 * @author YAPPC Platform
 * @version 1.0
 * @since Session 14C (GPU Acceleration)
 */
public final class GPUDetector {

  private static final Logger log = LoggerFactory.getLogger(GPUDetector.class);

  private static final Pattern GPU_NAME_PATTERN = Pattern.compile("GPU\\s+\\d+:\\s+(.+?)\\s+\\(");
  private static final Pattern GPU_MEMORY_PATTERN = Pattern.compile("(\\d+)MiB\\s+/\\s+(\\d+)MiB");

  /** Cached GPU availability (avoid repeated system calls) */
  private static Boolean gpuAvailableCache = null;

  /** Cached GPU info */
  private static GPUInfo gpuInfoCache = null;

  /**
   * Check if GPU is available for LLM inference.
   *
   * <p>Checks in order:
   *
   * <ol>
   *   <li>nvidia-smi command availability
   *   <li>CUDA_VISIBLE_DEVICES environment variable
   *   <li>Docker GPU device visibility
   * </ol>
   *
   * @return true if GPU detected, false if CPU-only
   */
  public boolean isGPUAvailable() {
    if (gpuAvailableCache != null) {
      return gpuAvailableCache;
    }

    // Method 1: Check nvidia-smi
    try {
      Process process = new ProcessBuilder("nvidia-smi").start();
      int exitCode = process.waitFor();

      if (exitCode == 0) {
        log.info("✓ GPU detected via nvidia-smi");
        gpuAvailableCache = true;
        return true;
      }
    } catch (Exception e) {
      log.debug("nvidia-smi not available: {}", e.getMessage());
    }

    // Method 2: Check environment variables
    String cudaDevices = System.getenv("CUDA_VISIBLE_DEVICES");
    String nvidiaDevices = System.getenv("NVIDIA_VISIBLE_DEVICES");

    if (cudaDevices != null && !cudaDevices.isEmpty() && !cudaDevices.equals("-1")) {
      log.info("✓ GPU detected via CUDA_VISIBLE_DEVICES={}", cudaDevices);
      gpuAvailableCache = true;
      return true;
    }

    if (nvidiaDevices != null && !nvidiaDevices.isEmpty() && !nvidiaDevices.equals("none")) {
      log.info("✓ GPU detected via NVIDIA_VISIBLE_DEVICES={}", nvidiaDevices);
      gpuAvailableCache = true;
      return true;
    }

    log.warn("⚠ No GPU detected, LLM inference will use CPU (slower)");
    log.info("To enable GPU:");
    log.info("  1. Install NVIDIA drivers + CUDA toolkit");
    log.info("  2. Install nvidia-docker2: apt-get install nvidia-docker2");
    log.info("  3. Add --gpus all to docker run command");

    gpuAvailableCache = false;
    return false;
  }

  /**
   * Get detailed GPU information.
   *
   * @return GPU info if available, empty if CPU-only
   */
  public Optional<GPUInfo> getGPUInfo() {
    if (!isGPUAvailable()) {
      return Optional.empty();
    }

    if (gpuInfoCache != null) {
      return Optional.of(gpuInfoCache);
    }

    try {
      Process process = new ProcessBuilder("nvidia-smi").start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

      String line;
      String gpuName = "Unknown GPU";
      int memoryTotalMB = 0;
      int memoryUsedMB = 0;

      while ((line = reader.readLine()) != null) {
        // Parse GPU name
        Matcher nameMatcher = GPU_NAME_PATTERN.matcher(line);
        if (nameMatcher.find()) {
          gpuName = nameMatcher.group(1).trim();
        }

        // Parse memory
        Matcher memoryMatcher = GPU_MEMORY_PATTERN.matcher(line);
        if (memoryMatcher.find()) {
          memoryUsedMB = Integer.parseInt(memoryMatcher.group(1));
          memoryTotalMB = Integer.parseInt(memoryMatcher.group(2));
        }
      }

      gpuInfoCache =
          new GPUInfo(gpuName, memoryTotalMB, memoryUsedMB, memoryTotalMB - memoryUsedMB);

      log.info("GPU Info: {}", gpuInfoCache);
      return Optional.of(gpuInfoCache);

    } catch (Exception e) {
      log.warn("Failed to get GPU info: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Get recommended GPU configuration for Ollama.
   *
   * @return Configuration recommendations
   */
  public GPUConfig getRecommendedConfig() {
    Optional<GPUInfo> gpuInfo = getGPUInfo();

    if (gpuInfo.isEmpty()) {
      // CPU-only configuration
      return new GPUConfig(
          0, // No GPU layers
          1, // Single GPU (none available)
          8, // CPU threads
          2, // Parallel requests (CPU limited)
          false // GPU disabled
          );
    }

    GPUInfo info = gpuInfo.get();
    int availableMemoryMB = info.memoryFreeMB();

    // Estimate GPU layers based on available memory
    // llama3.2 (3B params): ~6GB VRAM for full offload (33 layers)
    // Each layer ≈ 180MB
    int recommendedLayers = Math.min(33, availableMemoryMB / 180);

    // More memory = more parallel requests
    int parallelRequests = availableMemoryMB > 8000 ? 8 : availableMemoryMB > 4000 ? 4 : 2;

    return new GPUConfig(
        recommendedLayers,
        1, // Single GPU
        8, // CPU threads for preprocessing
        parallelRequests,
        true // GPU enabled
        );
  }

  /** GPU information record. */
  public record GPUInfo(String name, int memoryTotalMB, int memoryUsedMB, int memoryFreeMB) {
    public double memoryUsagePercent() {
      return (double) memoryUsedMB / memoryTotalMB * 100;
    }

    @Override
    public String toString() {
      return String.format(
          "%s [%dMB / %dMB (%.1f%% used)]",
          name, memoryUsedMB, memoryTotalMB, memoryUsagePercent());
    }
  }

  /** GPU configuration recommendation. */
  public record GPUConfig(
      int gpuLayers, // Number of layers to offload to GPU
      int gpuCount, // Number of GPUs to use
      int cpuThreads, // CPU threads for preprocessing
      int parallelRequests, // Max parallel requests
      boolean gpuEnabled // Whether GPU is enabled
      ) {
    @Override
    public String toString() {
      if (!gpuEnabled) {
        return "CPU-only (GPU disabled)";
      }

      return String.format(
          "GPU: %d layers, %d parallel requests, %d CPU threads",
          gpuLayers, parallelRequests, cpuThreads);
    }

    /** Get environment variables for Ollama configuration. */
    public String toEnvironmentVariables() {
      return String.format(
          "OLLAMA_NUM_GPU=%d\n"
              + "OLLAMA_GPU_LAYERS=%d\n"
              + "OLLAMA_NUM_THREAD=%d\n"
              + "OLLAMA_NUM_PARALLEL=%d\n"
              + "OLLAMA_FLASH_ATTENTION=%d",
          gpuEnabled ? gpuCount : 0, gpuLayers, cpuThreads, parallelRequests, gpuEnabled ? 1 : 0);
    }
  }

  /** Print GPU detection report to console. */
  public void printReport() {
    log.info("=".repeat(70));
    log.info("GPU Detection Report");
    log.info("=".repeat(70));

    if (isGPUAvailable()) {
      Optional<GPUInfo> info = getGPUInfo();
      if (info.isPresent()) {
        GPUInfo gpu = info.get();
        log.info("Status: ✓ GPU Available");
        log.info("Model: {}", gpu.name());
        log.info(
            "Memory: {}MB total, {}MB used, {}MB free",
            gpu.memoryTotalMB(),
            gpu.memoryUsedMB(),
            gpu.memoryFreeMB());
        log.info("Usage: {:.1f}%", gpu.memoryUsagePercent());
      }

      GPUConfig config = getRecommendedConfig();
      log.info("");
      log.info("Recommended Configuration:");
      log.info("  GPU Layers: {} / 33", config.gpuLayers());
      log.info("  Parallel Requests: {}", config.parallelRequests());
      log.info("  CPU Threads: {}", config.cpuThreads());

    } else {
      log.warn("Status: ✗ No GPU detected");
      log.info("LLM inference will use CPU (slower)");
      log.info("");
      log.info("To enable GPU acceleration:");
      log.info("  1. Install NVIDIA drivers + CUDA 12.x");
      log.info("  2. Install nvidia-docker2 runtime");
      log.info("  3. Add '--gpus all' to docker run command");
    }

    log.info("=".repeat(70));
  }
}
