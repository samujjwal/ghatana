package com.ghatana.platform.testing.activej;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension that provides a TestExecutionContext for each test method.
 * This ensures proper cleanup of all resources including EventLoops, connections, etc.
 * 
 * Usage:
 * <pre>
 * &#64;ExtendWith(TestExecutionExtension.class)
 * class MyTest {
 *     void testSomething(TestExecutionContext context) {
 *         var runner = context.createEventloopRunner();
 *         // Test code here...
 *         // Context and all resources automatically cleaned up after test
 *     }
 * }
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Test execution extension
 * @doc.layer core
 * @doc.pattern Component
*/
public class TestExecutionExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = 
        ExtensionContext.Namespace.create(TestExecutionExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        String testName = getTestName(context);
        TestExecutionContext testContext = new TestExecutionContext(testName);
        context.getStore(NAMESPACE).put("context", testContext);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestExecutionContext testContext = context.getStore(NAMESPACE)
            .remove("context", TestExecutionContext.class);
        if (testContext != null) {
            testContext.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(TestExecutionContext.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get("context", TestExecutionContext.class);
    }

    private String getTestName(ExtensionContext context) {
        return context.getTestClass().map(Class::getSimpleName).orElse("UnknownTest") +
               "_" + context.getDisplayName().replaceAll("\\W+", "_");
    }
}