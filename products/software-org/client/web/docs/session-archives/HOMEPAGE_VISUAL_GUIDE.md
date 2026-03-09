# HomePage Visual Preview

## What Users Will See

### Desktop View (1024px+)

```
╔════════════════════════════════════════════════════════════════════════╗
║                                                                        ║
║           Software Organization Platform                              ║
║              AI-First DevSecOps Control Center                         ║
║                                                                        ║
║   Unified platform for orchestrating software delivery, managing       ║
║   compliance, and leveraging AI for operational excellence.            ║
║                                                                        ║
╚════════════════════════════════════════════════════════════════════════╝

┌────────────────────┬────────────────────┬────────────────────┐
│   9 Feature        │    Real-time       │     AI-Driven      │
│   Areas            │    Data Updates    │     Insights       │
└────────────────────┴────────────────────┴────────────────────┘

┌─────────────────────────────┬─────────────────────────────┬──────────────────────────────┐
│ 📊 Control Tower            │ 🏢 Organization            │ ⚙️ Operations                │
│                             │                            │                              │
│ Real-time KPI metrics,      │ Manage departments, teams  │ Workflows, event streams,    │
│ AI insights, and event      │ and organizational         │ incident management          │
│ timeline                    │ structure                  │                              │
│                             │                            │                   →          │
├─────────────────────────────┼─────────────────────────────┼──────────────────────────────┤
│ 📈 Analytics                │ 🤖 AI & ML                 │ 🔒 Security                 │
│                             │                            │                              │
│ Reports, audit trails, and  │ Model catalog, pattern     │ Security posture,            │
│ performance metrics          │ simulator, and AI insights │ compliance, vulnerability    │
│                             │                            │                              │
│                   →          │                   →        │                   →          │
├─────────────────────────────┼─────────────────────────────┼──────────────────────────────┤
│ 💬 HITL Console             │ ⚗️ Event Simulator         │ ⚙️ Settings                 │
│                             │                            │                              │
│ Human-in-the-loop decision  │ Test scenarios and         │ Configuration, preferences   │
│ management and approvals    │ simulate event patterns    │ and integrations             │
│                             │                            │                              │
│                   →          │                   →        │                   →          │
└─────────────────────────────┴─────────────────────────────┴──────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  Get Started                                                            │
│                                                                         │
│  Start by viewing the Control Tower dashboard to see real-time KPIs     │
│  and AI insights, or explore any feature area from the sidebar          │
│  navigation.                                                            │
│                                                                         │
│                      [ View Control Tower → ]                           │
└─────────────────────────────────────────────────────────────────────────┘

Use the sidebar navigation to switch between feature areas anytime.
Each section provides specialized tools for managing your software organization.
```

### Mobile View (375px)

```
╔════════════════╗
║                ║
║ Software       ║
║ Organization   ║
║ Platform       ║
║                ║
║ AI-First       ║
║ DevSecOps      ║
║                ║
╠════════════════╣
║ 9 Features     ║
║ Real-time      ║
║ AI-Driven      ║
╠════════════════╣
║ 📊 Control     ║
║ Tower          ║
║ Real-time KPI  ║
║ metrics, AI    ║
║ insights, and  ║
║ event timeline ║
║         →      ║
╠════════════════╣
║ 🏢 Org         ║
║ Manage depts   ║
║ teams, struct  ║
║         →      ║
╠════════════════╣
║ ⚙️ Operations  ║
║ Workflows,     ║
║ events,        ║
║ incidents      ║
║         →      ║
╠════════════════╣
│ ...more cards │
╠════════════════╣
║ [ Get Started ]║
╚════════════════╝
```

## Color Scheme

Each feature card has a distinct color for visual identification:

- 📊 **Control Tower**: Blue (bg-blue-50 / hover: bg-blue-100)
- 🏢 **Organization**: Purple (bg-purple-50 / hover: bg-purple-100)
- ⚙️ **Operations**: Amber (bg-amber-50 / hover: bg-amber-100)
- 📈 **Analytics**: Green (bg-green-50 / hover: bg-green-100)
- 🤖 **AI & ML**: Pink (bg-pink-50 / hover: bg-pink-100)
- 🔒 **Security**: Red (bg-red-50 / hover: bg-red-100)
- 💬 **HITL Console**: Cyan (bg-cyan-50 / hover: bg-cyan-100)
- ⚗️ **Event Simulator**: Indigo (bg-indigo-50 / hover: bg-indigo-100)
- ⚙️ **Settings**: Slate (bg-slate-50 / hover: bg-slate-100)

## Dark Mode

In dark mode, colors are inverted:
- Control Tower: Dark Blue (bg-blue-950 / hover: bg-blue-900)
- Organization: Dark Purple (bg-purple-950 / hover: bg-purple-900)
- etc.

Text colors also adjust:
- Headings: White (text-white)
- Descriptions: Light Gray (text-slate-400)
- Borders: Dark Gray (border-slate-700)

## Interactive Elements

### Feature Card Hover Effect
- Scales up slightly (transform scale-105)
- Shows shadow (shadow-lg)
- Arrow on right becomes visible (opacity-0 → opacity-100)
- Smooth transition (200ms)

### Button Hover
- Control Tower button: Blue → Darker Blue
- Smooth color transition

### Links
- All feature cards are clickable links (`<Link>` from React Router)
- Navigate to respective pages without page reload
- Loading state shows "Loading..." fallback briefly

## Accessibility

- Semantic HTML structure
- Proper heading hierarchy (h1, h2, h3)
- Links have clear text content
- Color is not the only way to distinguish features (uses emojis + text)
- Responsive breakpoints ensure readability on all sizes

## Performance

- HomePage component is lazy-loaded (React.lazy)
- Uses Suspense with LoadingFallback during load
- No external icon libraries (uses Unicode arrows and emojis)
- Pure Tailwind CSS (no additional CSS-in-JS)
- Minimal JavaScript - mostly static layout

## Browser Support

- Modern browsers (Chrome, Firefox, Safari, Edge)
- Mobile browsers (iOS Safari, Chrome Mobile)
- CSS Grid and Flexbox support required
- TailwindCSS 3.4+ support

## Interactions

1. **Click Feature Card**: Navigate to that feature's page
2. **Hover Feature Card**: Card scales up, arrow appears, shadow grows
3. **Click Get Started**: Jump to Control Tower dashboard
4. **Sidebar Click**: Navigate to same pages (independent of HomePage)
5. **Mobile Menu**: Opens sidebar on mobile view

## Related Pages

- Feature cards link to: `/dashboard`, `/departments`, `/workflows`, `/hitl`, `/models`, `/export`, `/reports`, `/settings`, etc.
- Users can return to homepage: `/` or by clicking logo/home link
- All pages maintained in sidebar navigation

## Loading Behavior

- When clicking a feature card:
  1. LoadingFallback appears briefly ("Loading...")
  2. Feature page lazy-loads
  3. Smooth transition to content
  4. Typical load time: <1 second

---

**Version**: 1.0  
**Created**: 2025-11-22  
**Status**: Ready for production  
**Tested On**: Desktop, Tablet, Mobile
