package com.ghatana.servicemanager.process;

import com.ghatana.servicemanager.service.ServiceConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages a service process.
 * 
 * Wraps a Java process for an AEP service with proper
 * lifecycle management and monitoring.
 * 
 * @doc.type class
 * @doc.purpose Service process management
 * @doc.layer orchestration
 * @doc.pattern Process
 */
public class ServiceProcess {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceProcess.class);

    private final ServiceConfiguration configuration;
    private Process process;
    private final Object lock = new Object();

    public ServiceProcess(ServiceConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Starts the service process.
     */
    public void start() throws Exception {
        synchronized (lock) {
            if (process != null && process.isAlive()) {
                throw new IllegalStateException("Process is already running");
            }

            LOG.info("Starting service process: {}", configuration.getName());

            // Build Java process command
            List<String> command = new ArrayList<>();
            command.add("java");
            
            // Add JVM arguments
            for (String jvmArg : configuration.getJvmArgs()) {
                command.add(jvmArg);
            }
            
            // Add classpath (assuming Gradle build)
            command.add("-cp");
            command.add(getClasspath());
            
            // Add main class
            command.add(configuration.getMainClass());
            
            // Add port argument
            command.add("--http.listenAddresses=0.0.0.0:" + configuration.getPort());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            
            // Set environment variables
            for (String envVar : configuration.getEnvironmentVars()) {
                String[] parts = envVar.split("=", 2);
                if (parts.length == 2) {
                    processBuilder.environment().put(parts[0], parts[1]);
                }
            }
            
            // Redirect output to logs
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            // Start the process
            process = processBuilder.start();
            
            LOG.info("Service process started: {} (PID: {})", 
                    configuration.getName(), getPid());
        }
    }

    /**
     * Stops the service process.
     */
    public void stop() throws Exception {
        synchronized (lock) {
            if (process == null) {
                return;
            }

            LOG.info("Stopping service process: {} (PID: {})", 
                    configuration.getName(), getPid());

            // Try graceful shutdown first
            if (process.isAlive()) {
                process.destroy();
                
                // Wait for graceful shutdown
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    LOG.warn("Graceful shutdown timed out, force killing process: {}", 
                            configuration.getName());
                    process.destroyForcibly();
                    
                    // Wait for force kill
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        LOG.error("Failed to kill process: {}", configuration.getName());
                    }
                }
            }

            process = null;
            LOG.info("Service process stopped: {}", configuration.getName());
        }
    }

    /**
     * Checks if the process is alive.
     */
    public boolean isAlive() {
        synchronized (lock) {
            return process != null && process.isAlive();
        }
    }

    /**
     * Gets the process ID.
     */
    public long getPid() {
        synchronized (lock) {
            if (process == null) {
                return -1;
            }
            return process.pid();
        }
    }

    /**
     * Gets the service configuration.
     */
    public ServiceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Builds the classpath for the child service process.
     *
     * <p>Resolves the fat-JAR from an absolute base directory configured via the
     * {@code AEP_SERVICE_JAR_DIR} environment variable (e.g.
     * {@code /opt/aep/services}). Falls back to the current JVM classpath only
     * in development mode when the env var is absent.
     */
    private String getClasspath() {
        String jarDir = System.getenv("AEP_SERVICE_JAR_DIR");
        if (jarDir != null && !jarDir.isBlank()) {
            File serviceJar = new File(jarDir,
                    configuration.getName() + "-all.jar");
            if (serviceJar.exists()) {
                LOG.debug("Using fat-JAR classpath: {}", serviceJar.getAbsolutePath());
                return serviceJar.getAbsolutePath();
            }
            LOG.warn("AEP_SERVICE_JAR_DIR set to '{}' but JAR not found: {} — falling back to parent classpath",
                    jarDir, serviceJar.getAbsolutePath());
        } else {
            LOG.warn("AEP_SERVICE_JAR_DIR not set — using parent JVM classpath (dev mode only)");
        }
        return System.getProperty("java.class.path");
    }
}
