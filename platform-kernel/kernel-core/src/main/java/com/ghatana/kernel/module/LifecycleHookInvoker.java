package com.ghatana.kernel.module;

import com.ghatana.kernel.annotation.PostInitialize;
import com.ghatana.kernel.annotation.PostStop;
import com.ghatana.kernel.annotation.PreStart;

import java.lang.reflect.Method;

/**
 * Invokes lifecycle hook annotations on kernel modules.
 *
 * @doc.type class
 * @doc.purpose Reflective lifecycle hook execution for module annotations
 * @doc.layer core
 * @doc.pattern Lifecycle, Reflection
 */
public final class LifecycleHookInvoker {

    public void invokePostInitialize(Object target) {
        invokeAnnotatedMethods(target, PostInitialize.class);
    }

    public void invokePreStart(Object target) {
        invokeAnnotatedMethods(target, PreStart.class);
    }

    public void invokePostStop(Object target) {
        invokeAnnotatedMethods(target, PostStop.class);
    }

    private void invokeAnnotatedMethods(Object target, Class<? extends java.lang.annotation.Annotation> annotationType) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(annotationType)) {
                continue;
            }
            if (method.getParameterCount() != 0) {
                throw new IllegalStateException("Lifecycle hook methods must have zero parameters: " + method.getName());
            }
            try {
                method.setAccessible(true);
                method.invoke(target);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(
                    "Failed invoking lifecycle hook " + method.getName() + " on " + target.getClass().getName(),
                    exception
                );
            }
        }
    }
}
