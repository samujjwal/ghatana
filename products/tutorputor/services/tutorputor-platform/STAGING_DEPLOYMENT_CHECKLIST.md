# Tutorputor Phase 2 - Staging Deployment Verification Checklist

**Document**: Deployment Readiness  
**Status**: READY FOR STAGING ✅  
**Risk Level**: LOW-MEDIUM (verified by real cryptographic testing + integration tests)

---

## Pre-Deployment Verification (REQUIRED)

### Infrastructure Verification (Yellow-Flag Items)

**1. Session Storage Backend**
- [ ] Redis service running and accessible
  - Run: `redis-cli PING` → should return `PONG`
- [ ] Redis is configured for session storage
  - Verify `REDIS_URL` environment variable is set
  - Example: `redis://localhost:6379`
- [ ] Session data persists across service restarts
  - Create session → stop service → start service → verify session still exists

**2. Audit Log Persistence**
- [ ] Database audit log table exists
  ```sql
  SELECT * FROM audit_logs LIMIT 1;
  ```
- [ ] Audit log table constraints are correct
  - [ ] Primary key on (id)
  - [ ] Foreign key on user_id
  - [ ] Index on timestamp for query performance
  - [ ] Verify immutability constraint (no UPDATE/DELETE allowed on deployed record)

**3. LTI Nonce Table**
- [ ] Database nonce table exists
  ```sql
  SELECT * FROM lti_nonces LIMIT 1;
  ```
- [ ] Unique constraint exists on (tenant_id, nonce)
  - Run: `\d lti_nonces` (PostgreSQL) or equivalent
  - Should show: `UNIQUE (tenant_id, nonce)`
- [ ] TTL mechanism to clean old nonces (24-hour expiration)
  - Verify: Background job or database trigger that deletes nonces older than 24 hours

**4. Session Table Schema**
- [ ] Session table has required columns
  ```sql
  SELECT column_name, data_type 
  FROM information_schema.columns 
  WHERE table_name = 'sessions';
  ```
  - [ ] `session_id` (primary key)
  - [ ] `user_id` (foreign key)
  - [ ] `password_hash_version` (integer, tracks password update epoch)
  - [ ] `created_at` (timestamp)
  - [ ] `expires_at` (timestamp)
  - [ ] `tenant_id` (for multi-tenant isolation)

---

## Functional Verification (TEST SCENARIOS)

### A. JWT Signature Validation
**Objective**: Verify real JWT cryptography is working

**Steps**:
1. Generate a valid JWT with correct secret
   ```bash
   curl -X POST http://localhost:3000/auth/login \
     -d '{"email":"test@example.com","password":"password"}' \
     -H "Content-Type: application/json"
   ```
   - Expected: 200 OK, JWT token returned

2. Attempt to use JWT with tampered payload
   ```bash
   # Manually modify JWT payload (change role: student → admin)
   curl -X GET http://localhost:3000/api/create-content \
     -H "Authorization: Bearer <tampered-jwt>"
   ```
   - Expected: 401 Unauthorized, "Invalid token signature"

3. Attempt to use JWT with algorithm:none
   - Expected: 401 Unauthorized, "Algorithm 'none' not allowed"

**Expected Outcome**: ✅ All JWT attempts validated correctly

---

### B. RBAC Endpoint Access Control
**Objective**: Verify 5 roles enforce proper endpoint access

**Test Template** (repeat for all 5 roles):
```bash
# 1. Login as student
STUDENT_JWT=$(curl -s -X POST http://localhost:3000/auth/login \
  -d '{"email":"student@ghatana.ai","password":"password"}' | jq -r '.token')

# 2. Student tries to access teacher-only endpoint (should fail)
curl -X GET http://localhost:3000/api/manage-grades \
  -H "Authorization: Bearer $STUDENT_JWT"
# Expected: 403 Forbidden

# 3. Teacher tries to access student's endpoint (should succeed)
TEACHER_JWT=$(curl -s -X POST http://localhost:3000/auth/login \
  -d '{"email":"teacher@ghatana.ai","password":"password"}' | jq -r '.token')

curl -X GET http://localhost:3000/api/view-own-grades \
  -H "Authorization: Bearer $TEACHER_JWT"
# Expected: 200 OK (teacher is superset of student)
```

**Roles to Test**:
- [ ] Student: `view-own-grades`, `submit-assignment` (2 endpoints)
- [ ] Teacher: + `manage-grades`, `view-class-roster` (3 endpoints)
- [ ] Creator: `create-content` (1 unique endpoint)
- [ ] Admin: + `manage-users`, `view-system-config` (3 endpoints)
- [ ] Superadmin: + all remaining endpoints

**Expected Outcome**: ✅ Each role blocked from higher-privilege endpoints (403)

---

### C. Rate Limiting Enforcement
**Objective**: Verify rate limiting blocks after 5 failed attempts

**Steps**:
1. Make 5 failed login attempts from same IP
   ```bash
   for i in {1..5}; do
     curl -X POST http://localhost:3000/auth/login \
       -d '{"email":"test@example.com","password":"wrong"}' \
       -H "Content-Type: application/json"
     echo "Attempt $i"
   done
   ```
   - Attempts 1-4: Expected 401 Unauthorized
   - Attempt 5: Expected 429 Too Many Requests

2. Make 6th attempt immediately
   ```bash
   curl -X POST http://localhost:3000/auth/login \
     -d '{"email":"test@example.com","password":"wrong"}' \
     -H "Content-Type: application/json" -v
   ```
   - Expected: 429 Too Many Requests
   - Check header: `Retry-After: 300` (5 minutes)

3. Wait 5 minutes, attempt again
   - Expected: 401 Unauthorized (rate limit reset, back to normal auth flow)

**Expected Outcome**: ✅ Rate limiting enforced, Retry-After header present

---

### D. Audit Logging Immutability
**Objective**: Verify audit logs cannot be modified after creation

**Steps**:
1. Trigger an action that generates an audit log (e.g., failed login)
   ```bash
   curl -X POST http://localhost:3000/auth/login \
     -d '{"email":"test@example.com","password":"wrong"}'
   ```

2. Attempt to manually update audit log in database
   ```sql
   UPDATE audit_logs 
   SET action = 'modified' 
   WHERE user_id = 'test-user';
   ```
   - Expected: Row update fails (immutability constraint)

3. Attempt to delete audit log
   ```sql
   DELETE FROM audit_logs WHERE id = '<log-id>';
   ```
   - Expected: Row delete fails (immutability constraint)

**Expected Outcome**: ✅ Database prevents all audit log modifications

---

### E. LTI Nonce Replay Prevention
**Objective**: Verify nonce cannot be reused

**Steps**:
1. Make first LTI auth request with nonce
   ```bash
   curl -X POST http://localhost:3000/api/lti/auth \
     -d '{"nonce":"nonce-abc123","client_id":"..."}' \
     -H "Content-Type: application/json"
   ```
   - Expected: 200 OK

2. Replay the exact same request immediately
   ```bash
   curl -X POST http://localhost:3000/api/lti/auth \
     -d '{"nonce":"nonce-abc123","client_id":"..."}' \
     -H "Content-Type: application/json"
   ```
   - Expected: 401 Unauthorized, "Nonce already used"

**Expected Outcome**: ✅ Nonce replay rejected

---

### F. Session Cascade on Password Change
**Objective**: Verify all user sessions invalidated when password changes

**Steps**:
1. Login and create 3 sessions from different devices
   ```bash
   SESSION1=$(curl -s -X POST http://localhost:3000/auth/login \
     -H "User-Agent: Device-1" | jq -r '.sessionId')
   # (repeat for SESSION2, SESSION3)
   ```

2. Change password from one session
   ```bash
   curl -X POST http://localhost:3000/auth/change-password \
     -d '{"oldPassword":"password","newPassword":"newpassword"}' \
     -H "Authorization: Bearer $SESSION1"
   ```
   - Expected: 200 OK, new session returned

3. Attempt to use old sessions (SESSION2, SESSION3)
   ```bash
   curl -X GET http://localhost:3000/api/user-profile \
     -H "Authorization: Bearer $SESSION2"
   ```
   - Expected: 401 Unauthorized, "Session invalidated due to password change"

4. Verify new session works
   ```bash
   curl -X GET http://localhost:3000/api/user-profile \
     -H "Authorization: Bearer $<NEW_SESSION>"
   ```
   - Expected: 200 OK

**Expected Outcome**: ✅ All old sessions invalidated, new session required

---

## Performance Verification

### Load Testing
**Objective**: Verify authentication handling at scale

**Prerequisites**: Load testing tool (e.g., Apache JMeter, k6, or wrk)

**Test Case**:
```
- Concurrency: 100 concurrent users
- Duration: 5 minutes
- User Journey: Login → Make API call → Logout
- Success Criteria:
  - 99th percentile response time < 200ms
  - Error rate < 0.1% (no JWT decoding failures)
  - Rate limiting correctly blocks at 429 threshold
```

**Run Test**:
```bash
# Example with k6
k6 run --vus 100 --duration 5m load-test.js
```

**Expected Output**:
```
✅ http_req_duration: 95th p(150) ms, 99th p(180) ms
✅ http_requests: 30000 completed
✅ http_req_failed: 20 (0.07%) - all rate-limit 429s, expected
```

---

## Security Review

### Code Review Checklist
- [ ] JWT secret is not hardcoded in config files
- [ ] Password hashes use bcrypt or scrypt (not plaintext)
- [ ] Rate limiting counter is per-IP (not per-email)
- [ ] Audit logs capture IP address and user agent
- [ ] Error messages don't leak sensitive information (e.g., "Invalid credentials" not "Email not found")
- [ ] Session tokens are httpOnly and Secure cookie attributes set

### Security Penetration Testing (OPTIONAL)
- [ ] Run OWASP ZAP or equivalent on authentication endpoints
- [ ] Test SQL injection vectors (e.g., email field)
- [ ] Test XSS in error messages
- [ ] Test CSRF protection on state-changing endpoints (POST)
- [ ] Run threat model review against JWT implementation

---

## Sign-Off Criteria

All items below must be ✅ complete before production deployment:

- [ ] All Infrastructure Verification tests pass (4/4 sections)
- [ ] All Functional Verification tests pass (6/6 scenarios)
- [ ] Load testing passes (100 concurrent users, <200ms p99)
- [ ] Security review completed (no critical vulnerabilities found)
- [ ] Security team approval obtained
- [ ] Deployment runbook reviewed and tested
- [ ] Rollback plan documented and tested

---

## Post-Deployment Monitoring (FIRST 24 HOURS)

Once deployed to production, monitor:

1. **Authentication metrics**:
   - Login success rate (should be > 95%)
   - Failed login rate (should stay < 5%)
   - Rate limit enforcement (should see ~0.1% of requests hitting 429)

2. **Audit logging**:
   - Audit logs being written at expected rate
   - No audit log update/delete errors
   - Audit log response time < 50ms

3. **JWT errors**:
   - Zero invalid signature errors (would indicate secret mismatch)
   - Zero "algorithm none" errors
   - JWT validation exceptions < 0.01%

4. **Session management**:
   - Session creation rate matches login rate
   - Session cascade invalidation working (observe on password changes)
   - No orphaned sessions (TTL cleanup working)

5. **Alerts to watch**:
   - Spike in 429 responses (rate limiting triggering)
   - Spike in 401 responses (potential attack or configuration issue)
   - Audit log table growth exceeding threshold
   - Redis connection failures
   - Database constraint violations

---

**Ready to proceed with staging deployment?** ✅

