package middleware

import (
    "context"
    "net/http"
    "strings"
    "time"

    iid "github.com/samujjwal/dcmaar/apps/server/internal/identity"
    telctx "github.com/samujjwal/dcmaar/apps/server/pkg/common/telemetry"
    "go.uber.org/zap"
)

type identityKey struct{}

// ContextWithIdentity stores identity in context.
func ContextWithIdentity(ctx context.Context, id *iid.Identity) context.Context {
    return context.WithValue(ctx, identityKey{}, id)
}

// IdentityFromContext retrieves identity from context.
func IdentityFromContext(ctx context.Context) *iid.Identity {
    if v, ok := ctx.Value(identityKey{}).(*iid.Identity); ok {
        return v
    }
    return nil
}

// IdentityMiddleware extracts identity from Authorization: Bearer <jwt> and caches it.
// This is a lightweight, non-validating parser intended for dev and test harnesses.
func IdentityMiddleware(cache *iid.Cache, logger *zap.Logger) func(next http.Handler) http.Handler {
    if cache == nil {
        // default small TTL cache if not provided
        cache = iid.NewCache(2 * time.Minute)
    }
    if logger == nil {
        logger = zap.NewNop()
    }
    return func(next http.Handler) http.Handler {
        return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            auth := r.Header.Get("Authorization")
            var id *iid.Identity
            if strings.HasPrefix(strings.ToLower(auth), "bearer ") {
                token := strings.TrimSpace(auth[len("Bearer "):])
                sub, roles := iid.ParseSubjectRoles(token)
                if sub != "" {
                    if cached := cache.Get(sub); cached != nil {
                        id = cached
                    } else {
                        id = &iid.Identity{Subject: sub, Roles: roles}
                        cache.Set(sub, id)
                    }
                }
            }

            if id != nil {
                // Enrich request-scoped logger with subject
                lg := telctx.LoggerFromContext(r.Context()).With(zap.String("subject", id.Subject))
                ctx := ContextWithIdentity(r.Context(), id)
                ctx = telctx.ContextWithLogger(ctx, lg)
                next.ServeHTTP(w, r.WithContext(ctx))
                return
            }
            next.ServeHTTP(w, r)
        })
    }
}
