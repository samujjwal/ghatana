package main

import (
    "net/http"
    "net/http/httptest"
    "testing"

    "go.uber.org/zap"
)

// Test that /whoami is gated by DevHelpers flag and responds when enabled.
func TestHTTPHelpers_WhoAmI_Gating(t *testing.T) {
    // Disabled: expect 404
    s := &Server{config: &Config{HTTPAddr: ":0", DevHelpers: false}, logger: zap.NewNop()}
    s.setupHTTPServer()
    req := httptest.NewRequest(http.MethodGet, "/whoami", nil)
    rr := httptest.NewRecorder()
    s.httpServer.Handler.ServeHTTP(rr, req)
    if rr.Code != http.StatusNotFound {
        t.Fatalf("expected 404 when DevHelpers disabled, got %d", rr.Code)
    }

    // Enabled: expect 200 and JSON body
    s2 := &Server{config: &Config{HTTPAddr: ":0", DevHelpers: true}, logger: zap.NewNop()}
    s2.setupHTTPServer()
    rr2 := httptest.NewRecorder()
    s2.httpServer.Handler.ServeHTTP(rr2, req)
    if rr2.Code != http.StatusOK {
        t.Fatalf("expected 200 when DevHelpers enabled, got %d", rr2.Code)
    }
    if ct := rr2.Header().Get("Content-Type"); ct == "" {
        t.Fatalf("expected Content-Type header, got empty")
    }
}

