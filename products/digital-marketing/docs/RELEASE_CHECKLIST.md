# DMOS Release Checklist

## Pre-Release

### Code Quality
- [ ] All tests pass (unit, integration, E2E)
- [ ] Code coverage thresholds met (95% line, 90% branch)
- [ ] No linting errors
- [ ] No security vulnerabilities (CodeQL, Trivy, TruffleHog)
- [ ] Accessibility tests pass

### Documentation
- [ ] CHANGELOG.md updated
- [ ] Migration scripts tested
- [ ] Environment variables documented
- [ ] Runbooks updated if needed

### Testing
- [ ] Manual testing completed
- [ ] E2E journey tests pass
- [ ] Accessibility tests pass
- [ ] Performance tests pass

## Release

### Database
- [ ] Database migrations run successfully
- [ ] Rollback plan documented
- [ ] Backup taken before migration

### Deployment
- [ ] Staging deployment successful
- [ ] Smoke tests pass on staging
- [ ] Production deployment successful
- [ ] Health checks pass
- [ ] Metrics are being collected

## Post-Release

### Monitoring
- [ ] Check error rates
- [ ] Check latency metrics
- [ ] Check connector health
- [ ] Check database performance
- [ ] Review alerts

### Verification
- [ ] Key user flows tested
- [ ] Data integrity verified
- [ ] Security checks pass
- [ ] Performance benchmarks met

### Documentation
- [ ] Release notes published
- [ ] Version tagged in Git
- [ ] Runbook updated if needed

## Rollback Criteria

Rollback if:
- Error rate > 5%
- P95 latency > 2 seconds
- Database connection failures
- Critical security issue discovered
- Data corruption detected
