package middleware

import (
    "encoding/base64"
    "encoding/json"
    "net/http"
    "net/http/httptest"
    "testing"
    "time"

    iid "github.com/samujjwal/dcmaar/apps/server/internal/identity"
    "go.uber.org/zap"
)

func makeJWT(t *testing.T, sub string, roles []string) string {
    t.Helper()
    header := map[string]any{"alg": "none", "typ": "JWT"}
    payload := map[string]any{"sub": sub, "roles": roles}
    h, _ := json.Marshal(header)
    p, _ := json.Marshal(payload)
    enc := func(b []byte) string { return base64.RawURLEncoding.EncodeToString(b) }
    return enc(h) + "." + enc(p) + "."
}

func TestParseJWTSubjectRoles(t *testing.T) {
    token := makeJWT(t, "u1", []string{"admin", "dev"})
    sub, roles := iid.ParseSubjectRoles(token)
    if sub != "u1" {
        t.Fatalf("expected sub=u1 got %q", sub)
    }
    if len(roles) != 2 || roles[0] != "admin" || roles[1] != "dev" {
        t.Fatalf("unexpected roles: %+v", roles)
    }

    // Invalid token
    if s, r := iid.ParseSubjectRoles("bad.token"); s != "" || len(r) != 0 {
        t.Fatalf("expected empty for invalid token, got sub=%q roles=%v", s, r)
    }
}

func TestIdentityMiddleware_ContextEnrichment(t *testing.T) {
    cache := iid.NewCache(1 * time.Minute)
    logger := zap.NewNop()
    sub := "tester"
    token := makeJWT(t, sub, []string{"role1"})

    var seen *iid.Identity
    h := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        seen = IdentityFromContext(r.Context())
        w.WriteHeader(http.StatusOK)
    })

    wrapped := IdentityMiddleware(cache, logger)(h)

    req := httptest.NewRequest(http.MethodGet, "/whoami", nil)
    req.Header.Set("Authorization", "Bearer "+token)
    rr := httptest.NewRecorder()
    wrapped.ServeHTTP(rr, req)

    if rr.Code != http.StatusOK {
        t.Fatalf("unexpected status: %d", rr.Code)
    }
    if seen == nil || seen.Subject != sub {
        t.Fatalf("identity not propagated, got %#v", seen)
    }

    // Second request should hit cache
    rr2 := httptest.NewRecorder()
    wrapped.ServeHTTP(rr2, req)
    if rr2.Code != http.StatusOK {
        t.Fatalf("unexpected status on second call: %d", rr2.Code)
    }
}
