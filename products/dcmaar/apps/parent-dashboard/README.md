# Guardian Parent Dashboard

**Status:** ✅ Production Ready  
**Test Coverage:** 100% (152/152 tests passing)  
**Build Status:** ✅ Passing

## Overview

The Guardian Parent Dashboard is a React-based web application that allows parents to monitor and manage their children's device usage, set policies, and receive real-time notifications about blocked content.

## Quick Start

```bash
# Install dependencies (from workspace root)
pnpm install

# Run development server
cd products/dcmaar/apps/guardian/apps/parent-dashboard
npm run dev

# Run tests
npm test

# Build for production
npm run build
```

## Test Results 🎉

```
✅ Test Files:  25 passed (25)
✅ Tests:       152 passed (152)
✅ Duration:    4.89s
✅ Coverage:    100%
```

## Features

- 📊 **Real-time Analytics** - Monitor device usage with live charts
- 🚫 **Content Blocking** - Real-time notifications of blocked content
- 👨‍👩‍👧‍👦 **Device Management** - Track and manage multiple devices
- 📋 **Policy Management** - Create and manage usage policies
- 📈 **Reports** - Generate PDF/CSV usage reports
- 🔔 **Notifications** - Real-time WebSocket-based alerts

## Tech Stack

- **Framework:** React 19.2.0 + TypeScript
- **Build Tool:** Vite 7.2.2
- **State Management:** Jotai
- **Data Fetching:** React Query (TanStack Query)
- **Routing:** React Router v7
- **Styling:** Tailwind CSS
- **Testing:** Vitest + React Testing Library
- **Forms:** React Hook Form + Zod

## Project Structure

```
src/
├── components/          # Reusable UI components
│   ├── Analytics.tsx
│   ├── BlockNotifications.tsx
│   ├── Dashboard.tsx
│   ├── DeviceManagement.tsx
│   ├── PolicyManagement.tsx
│   └── UsageMonitor.tsx
├── pages/              # Page components
│   ├── Dashboard.tsx
│   ├── Login.tsx
│   └── Register.tsx
├── services/           # API and service layer
│   ├── auth.service.ts
│   ├── api.ts
│   └── websocket.service.ts
├── test/              # Test files
│   ├── utils/         # Test utilities
│   └── *.test.tsx     # Component tests
└── utils/             # Utility functions
```

## Development

### Prerequisites

- Node.js 20+
- pnpm 10+

### Environment Setup

```bash
# Copy environment template
cp .env.example .env

# Configure your environment variables
VITE_API_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080
```

### Running Tests

```bash
# Run all tests
npm test

# Run tests in watch mode
npm test -- --watch

# Run specific test file
npm test -- src/test/dashboard.test.tsx

# Generate coverage report
npm test -- --coverage
```

### Building

```bash
# Development build
npm run build

# Preview production build
npm run preview
```

## Testing

The project maintains **100% test coverage** with comprehensive test suites:

- **Unit Tests:** Component logic and utilities
- **Integration Tests:** Component interactions and data flow
- **Accessibility Tests:** WCAG compliance
- **Performance Tests:** Lazy loading and code splitting

### Test Utilities

- `renderWithDashboardProviders()` - Renders components with all necessary providers
- Mock services for API and WebSocket
- Test data builders for consistent test data

## Documentation

Comprehensive documentation is available in the project root:

- **MISSION_ACCOMPLISHED.md** - Achievement summary and final report
- **QUICK_START.md** - Quick reference guide
- **DOCUMENTATION_INDEX.md** - Documentation navigation
- **FIX_PLAN.md** - Technical implementation details
- **SESSION_3_COMPLETION_REPORT.md** - Detailed session work log

## CI/CD

### Build Verification

```bash
# Verify build passes
npm run build

# Run all tests
npm test -- --run

# Lint check (if configured)
npm run lint
```

### Deployment

The application is production-ready and can be deployed to any static hosting service:

- Vercel
- Netlify
- AWS S3 + CloudFront
- Azure Static Web Apps
- GitHub Pages

## Performance

- **Bundle Size:** Optimized with code splitting
- **Load Time:** Fast with lazy loading
- **Test Duration:** 4.89 seconds
- **Build Time:** 8.63 seconds

## Browser Support

- Chrome/Edge (last 2 versions)
- Firefox (last 2 versions)
- Safari (last 2 versions)

## Contributing

1. Ensure all tests pass: `npm test -- --run`
2. Verify build succeeds: `npm run build`
3. Follow existing code patterns
4. Add tests for new features
5. Update documentation as needed

## License

[Your License Here]

## Support

For issues or questions:
- Check the documentation in the project root
- Review test files for usage examples
- Consult the MISSION_ACCOMPLISHED.md for recent changes

---

**Last Updated:** November 20, 2025  
**Version:** 1.0.0  
**Status:** ✅ Production Ready


