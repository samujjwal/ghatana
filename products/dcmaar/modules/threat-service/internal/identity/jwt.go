package identity

import (
    "encoding/base64"
    "encoding/json"
    "strings"
)

// ParseSubjectRoles decodes an unsigned JWT (dev/testing) and returns subject and roles.
// This is a lightweight, non-validating helper intended for middleware/interceptors in dev/test.
func ParseSubjectRoles(token string) (string, []string) {
    parts := strings.Split(token, ".")
    if len(parts) < 2 {
        return "", nil
    }
    payload, err := base64.RawURLEncoding.DecodeString(parts[1])
    if err != nil {
        return "", nil
    }
    var m map[string]interface{}
    if err := json.Unmarshal(payload, &m); err != nil {
        return "", nil
    }
    sub, _ := m["sub"].(string)
    var roles []string
    if arr, ok := m["roles"].([]interface{}); ok {
        for _, v := range arr {
            if s, ok := v.(string); ok {
                roles = append(roles, s)
            }
        }
    }
    return sub, roles
}

