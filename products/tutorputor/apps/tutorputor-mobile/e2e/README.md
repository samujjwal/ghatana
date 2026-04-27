# Mobile E2E Tests (Maestro)

These flows use [Maestro](https://maestro.mobile.dev/) for end-to-end testing of the TutorPutor mobile app.

## Prerequisites

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

## Running Tests

```bash
# Run all flows
maestro test e2e/

# Run a specific flow
maestro test e2e/dashboard.yaml

# Run on Android
maestro test --device android e2e/

# Run on iOS simulator
maestro test --device ios e2e/
```

## Test Coverage

| Flow | Screens Covered | Key Assertions |
|------|-----------------|----------------|
| `login.yaml` | LoginScreen | Email/password form, sign-in navigation |
| `navigation.yaml` | All tabs, stack transitions | Tab labels visible, screens render |
| `dashboard.yaml` | DashboardScreen | Continue Learning, Quick Actions |
| `ai-tutor.yaml` | AITutorScreen | Chat input, message send, response |
| `modules.yaml` | ModulesScreen | Browse, filter, open module |
| `offline.yaml` | OfflineIndicator | Offline banner appears/disappears |
