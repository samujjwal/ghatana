package com.ghatana.security.controller;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.auth.AuthenticationProvider;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles authentication endpoints for the security module.
 
 *
 * @doc.type class
 * @doc.purpose Auth controller
 * @doc.layer core
 * @doc.pattern Controller
*/
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final List<AuthenticationProvider> authenticationProviders;
    private final JwtTokenProvider jwtTokenProvider;
    
    public AuthController(List<AuthenticationProvider> authenticationProviders, JwtTokenProvider jwtTokenProvider) {
        this.authenticationProviders = authenticationProviders;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    public Promise<HttpResponse> handleLogin(HttpRequest request) {

            return ResponseBuilder.ok()
                .json("{\"success\":true}").build().toPromise();


    }
}
