package com.ghatana.platform.testing.chaos;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * JUnit 5 extension that enables chaos injection during tests.
 *
 * <p>This extension reads {@link ChaosTest} annotations and configures
 * the appropriate chaos injection strategy for each test.</p>
 *
 * @doc.type class
 * @doc.purpose JUnit extension for chaos test lifecycle management
 * @doc.layer core
 * @doc.pattern Extension
 */
public class ChaosTestExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger logger = LoggerFactory.getLogger(ChaosTestExtension.class);
    private static final String CHAOS_CONTEXT_KEY = "chaosContext";

    @Override
    public void beforeEach(ExtensionContext context) {
        Optional<Method> testMethod = context.getTestMethod();
        if (testMethod.isEmpty()) {
            return;
        }

        ChaosTest chaosAnnotation = findChaosAnnotation(context);
        if (chaosAnnotation == null) {
            return;
        }

        ChaosContext chaosContext = new ChaosContext(
                chaosAnnotation.value(),
                chaosAnnotation.failureProbability(),
                chaosAnnotation.maxDurationMs()
        );

        getStore(context).put(CHAOS_CONTEXT_KEY, chaosContext);
        ChaosInjector.activate(chaosContext);

        logger.info("Chaos testing enabled: type={}, probability={}, maxDuration={}ms",
                chaosAnnotation.value(),
                chaosAnnotation.failureProbability(),
                chaosAnnotation.maxDurationMs());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ChaosContext chaosContext = getStore(context).remove(CHAOS_CONTEXT_KEY, ChaosContext.class);
        if (chaosContext != null) {
            ChaosInjector.deactivate();
            logger.info("Chaos testing disabled, injections: {}, failures: {}",
                    chaosContext.getInjectionCount(),
                    chaosContext.getFailureCount());
        }
    }

    private ChaosTest findChaosAnnotation(ExtensionContext context) {
        // Check method first
        Optional<Method> testMethod = context.getTestMethod();
        if (testMethod.isPresent()) {
            ChaosTest annotation = testMethod.get().getAnnotation(ChaosTest.class);
            if (annotation != null) {
                return annotation;
            }
        }

        // Check class
        Optional<Class<?>> testClass = context.getTestClass();
        if (testClass.isPresent()) {
            return testClass.get().getAnnotation(ChaosTest.class);
        }

        return null;
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
