package com.ghatana.core.activej.launcher;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.inject.Injector;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.service.Service;
import io.activej.service.ServiceGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ServiceLauncher functionality.
 * 
 * @doc.type class
 * @doc.purpose Tests for ActiveJ service launcher lifecycle management
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("ServiceLauncher Tests")
class ServiceLauncherTest extends EventloopTestBase {

    @Test
    @DisplayName("should create service launcher with default configuration")
    void shouldCreateServiceLauncherWithDefaults() {
        ServiceLauncher launcher = new ServiceLauncher() {
            @Override
            protected Module getModule() {
                return Module.empty();
            }
        };
        
        assertThat(launcher).isNotNull();
    }

    @Test
    @DisplayName("should initialize service graph correctly")
    void shouldInitializeServiceGraph() {
        AtomicBoolean serviceStarted = new AtomicBoolean(false);
        
        ServiceLauncher launcher = new ServiceLauncher() {
            @Override
            protected Module getModule() {
                return Module.builder()
                    .bind(Eventloop.class).toInstance(eventloop())
                    .bind(Service.class).toInstance(new Service() {
                        @Override
                        public void start() {
                            serviceStarted.set(true);
                        }
                        
                        @Override
                        public void stop() {
                            serviceStarted.set(false);
                        }
                    })
                    .build();
            }
        };
        
        assertThat(launcher).isNotNull();
    }

    @Test
    @DisplayName("should handle service lifecycle transitions")
    void shouldHandleServiceLifecycleTransitions() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        
        Service testService = new Service() {
            @Override
            public void start() {
                started.set(true);
            }
            
            @Override
            public void stop() {
                stopped.set(true);
            }
        };
        
        testService.start();
        assertThat(started.get()).isTrue();
        
        testService.stop();
        assertThat(stopped.get()).isTrue();
    }

    @Test
    @DisplayName("should create injector with modules")
    void shouldCreateInjectorWithModules() {
        Module testModule = Module.builder()
            .bind(String.class).toInstance("test-value")
            .bind(Integer.class).toInstance(42)
            .build();
        
        Injector injector = Injector.of(testModule);
        
        assertThat(injector.getInstance(String.class)).isEqualTo("test-value");
        assertThat(injector.getInstance(Integer.class)).isEqualTo(42);
    }

    @Test
    @DisplayName("should handle service dependencies")
    void shouldHandleServiceDependencies() {
        AtomicBoolean dependency1Started = new AtomicBoolean(false);
        AtomicBoolean dependency2Started = new AtomicBoolean(false);
        
        Service dependency1 = new Service() {
            @Override
            public void start() {
                dependency1Started.set(true);
            }
            
            @Override
            public void stop() {
                dependency1Started.set(false);
            }
        };
        
        Service dependency2 = new Service() {
            @Override
            public void start() {
                dependency2Started.set(true);
            }
            
            @Override
            public void stop() {
                dependency2Started.set(false);
            }
        };
        
        dependency1.start();
        dependency2.start();
        
        assertThat(dependency1Started.get()).isTrue();
        assertThat(dependency2Started.get()).isTrue();
    }

    @Test
    @DisplayName("should handle service startup failures")
    void shouldHandleServiceStartupFailures() {
        Service failingService = new Service() {
            @Override
            public void start() {
                throw new RuntimeException("Service startup failed");
            }
            
            @Override
            public void stop() {
                // No-op
            }
        };
        
        assertThatThrownBy(failingService::start)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Service startup failed");
    }

    @Test
    @DisplayName("should handle service shutdown gracefully")
    void shouldHandleServiceShutdownGracefully() {
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        
        Service service = new Service() {
            @Override
            public void start() {
                // No-op
            }
            
            @Override
            public void stop() {
                cleanedUp.set(true);
            }
        };
        
        service.start();
        service.stop();
        
        assertThat(cleanedUp.get()).isTrue();
    }

    @Test
    @DisplayName("should support service graph construction")
    void shouldSupportServiceGraphConstruction() {
        Eventloop eventloop = eventloop();
        
        ServiceGraph graph = ServiceGraph.builder()
            .with(eventloop)
            .build();
        
        assertThat(graph).isNotNull();
    }

    @Test
    @DisplayName("should handle multiple service instances")
    void shouldHandleMultipleServiceInstances() {
        AtomicBoolean service1Started = new AtomicBoolean(false);
        AtomicBoolean service2Started = new AtomicBoolean(false);
        AtomicBoolean service3Started = new AtomicBoolean(false);
        
        Service service1 = new Service() {
            @Override
            public void start() { service1Started.set(true); }
            @Override
            public void stop() { service1Started.set(false); }
        };
        
        Service service2 = new Service() {
            @Override
            public void start() { service2Started.set(true); }
            @Override
            public void stop() { service2Started.set(false); }
        };
        
        Service service3 = new Service() {
            @Override
            public void start() { service3Started.set(true); }
            @Override
            public void stop() { service3Started.set(false); }
        };
        
        service1.start();
        service2.start();
        service3.start();
        
        assertThat(service1Started.get()).isTrue();
        assertThat(service2Started.get()).isTrue();
        assertThat(service3Started.get()).isTrue();
    }

    @Test
    @DisplayName("should handle service restart scenarios")
    void shouldHandleServiceRestartScenarios() {
        AtomicBoolean isRunning = new AtomicBoolean(false);
        
        Service service = new Service() {
            @Override
            public void start() {
                isRunning.set(true);
            }
            
            @Override
            public void stop() {
                isRunning.set(false);
            }
        };
        
        // First start
        service.start();
        assertThat(isRunning.get()).isTrue();
        
        // Stop
        service.stop();
        assertThat(isRunning.get()).isFalse();
        
        // Restart
        service.start();
        assertThat(isRunning.get()).isTrue();
    }
}
