package com.ghatana.platform.http.server.servlet;

import io.activej.http.AsyncServlet;

/**
 * Small decorator interface used to wrap AsyncServlets.
 * Implementations receive the next servlet and must return a servlet that
 * applies middleware behavior before/after delegating to next.
 
 *
 * @doc.type interface
 * @doc.purpose Async servlet decorator
 * @doc.layer platform
 * @doc.pattern Interface
*/
public interface AsyncServletDecorator {
    AsyncServlet serve(AsyncServlet next);
}

