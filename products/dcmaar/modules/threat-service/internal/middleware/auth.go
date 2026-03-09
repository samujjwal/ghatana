package middleware

// TODO: Implement ExtensionAuthValidator when protobuf definitions include ExtensionAuth
// Currently commented out due to missing pb.ExtensionAuth structure

/*
import (
	"context"
	"errors"

	pb "github.com/samujjwal/dcmaar/proto/gen/go/pb"
)

// ExtensionAuthValidator validates extension auth using configured storage/services.
type ExtensionAuthValidator struct {
	Lookup func(ctx context.Context, extensionID string) (storedAPIKeyHash string, certFingerprint string, active bool, err error)
	// CompareHash should compare provided API key with stored hash in constant time.
	CompareHash func(storedHash, provided string) bool
	// Fingerprint returns a fingerprint (e.g., SHA256) for a provided cert DER.
	Fingerprint func(der []byte) string
}

func (v *ExtensionAuthValidator) ValidateExtensionAuth(ctx context.Context, auth *pb.ExtensionAuth) (string, error) {
	if auth == nil || auth.ExtensionId == "" || auth.ApiKey == "" {
		return "", errors.New("missing credentials")
	}
	storedHash, storedFP, active, err := v.Lookup(ctx, auth.ExtensionId)
	if err != nil {
		return "", err
	}
	if !active {
		return "", errors.New("extension inactive")
	}
	if !v.CompareHash(storedHash, auth.ApiKey) {
		return "", errors.New("invalid api key")
	}
	if len(auth.ClientCert) > 0 {
		fp := v.Fingerprint(auth.ClientCert)
		if fp == "" || (storedFP != "" && fp != storedFP) {
			return "", errors.New("certificate not recognized")
		}
	}
	return auth.ExtensionId, nil
}
*/
