package com.ghatana.security.tls;

import com.ghatana.security.config.TlsProperties;
import io.activej.common.MemSize;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Eager;
import io.activej.inject.annotation.Named;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.activej.reactor.net.ServerSocketSettings;
import io.activej.reactor.net.SocketSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Dependency injection module for TLS/SSL configuration.
 
 *
 * @doc.type class
 * @doc.purpose Tls module
 * @doc.layer core
 * @doc.pattern Component
*/
public class TlsModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(TlsModule.class);

    @Override
    protected void configure() {
        // Bind the TLS configuration service as a singleton
        bind(TlsConfigurationService.class).to(TlsConfigurationService.class);
        
        // Bind SSLContext as a singleton
        bind(SSLContext.class).to(SSLContext.class);
        // Bind socket settings as singletons
        bind(ServerSocketSettings.class).to(ServerSocketSettings.class);
        bind(SocketSettings.class).to(SocketSettings.class);
    }

    @Provides
    TlsProperties tlsProperties(com.ghatana.security.config.SecurityProperties securityProps) {
        return securityProps.getTls();
    }

    @Provides
    @Eager
    TlsConfigurationService tlsConfigurationService(Eventloop eventloop, TlsProperties tlsProperties) {
        TlsConfigurationService tlsService = new TlsConfigurationService(tlsProperties);
        try {
            tlsService.initialize();
        } catch (TlsConfigurationException e) {
            throw new RuntimeException("Failed to initialize TLS configuration service", e);
        }
        return tlsService;
    }

    // SSL/TLS configuration is now handled directly through SSLContext and SocketSettings/ServerSocketSettings

    @Provides
    @Named("tlsServerSocketSettings")
    public ServerSocketSettings tlsServerSocketSettings(TlsConfigurationService tlsService) {
        try {
            return tlsService.getServerSocketSettings();
        } catch (TlsConfigurationException e) {
            throw new RuntimeException("Failed to get server socket settings", e);
        }
    }

    @Provides
    @Named("tlsSocketSettings")
    public SocketSettings tlsSocketSettings(TlsConfigurationService tlsService) {
        try {
            return tlsService.getSocketSettings();
        } catch (TlsConfigurationException e) {
            throw new RuntimeException("Failed to get socket settings", e);
        }
    }

    @Provides
    @Eager
    SSLContext sslContext(TlsConfigurationService tlsService) {
        try {
            return tlsService.getSslContext();
        } catch (TlsConfigurationException e) {
            throw new RuntimeException("Failed to get SSL context", e);
        }
    }
    
    @Provides
    @Named("tlsServerSocketSettings")
    ServerSocketSettings serverSocketSettings(TlsProperties tlsProps) {
        return ServerSocketSettings.builder()
                .withReuseAddress(tlsProps.isReuseAddress())
                .withReceiveBufferSize(MemSize.bytes(tlsProps.getReceiveBufferSize()))
                .withBacklog(tlsProps.getBacklog())
                .build();

    }
    
    @Provides
    @Named("tlsClientSocketSettings")
    SocketSettings clientSocketSettings(TlsProperties tlsProps) {
        return SocketSettings.builder()
            .withTcpNoDelay(tlsProps.isTcpNoDelay())
                .withKeepAlive(tlsProps.isTcpKeepAlive())
                .withReceiveBufferSize(MemSize.bytes(tlsProps.getReceiveBufferSize()))
                .withSendBufferSize(MemSize.bytes(tlsProps.getSendBufferSize()))
                .withReuseAddress(tlsProps.isReuseAddress())
                .build();

    }

    @Provides
    Executor sslExecutor() {
        // Create a dedicated executor for SSL operations
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "ssl-executor");
            thread.setDaemon(true);
            return thread;
        });
    }
}
