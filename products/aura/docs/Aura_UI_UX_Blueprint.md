# Aura UI/UX System Blueprint

## Core Design Principles

1. **Minimal cognitive load** — surface the most relevant recommendation first; reduce the need to browse.
2. **Explainable recommendations** — every card tells the user _why_ the product is recommended. No black box.
3. **Fast product comparison** — comparing 2–3 products should require no more than 2 taps.
4. **Profile transparency** — users can always see what Aura knows about them and why it matters.
5. **Trust-first design** — affiliate labels, confidence levels, and data origin are always visible; nothing is hidden.
6. **Accessibility** — WCAG 2.1 AA compliance on web; equivalent standards on mobile.
7. **Outcome capture** — users can quickly report "worked for me," shade mismatch, irritation, or return without hunting through settings.

---

## Design System

- **Routing:** React Router v7 in framework mode — handles routing, data fetching, error boundaries, and progressive enhancement
- **Styling:** Tailwind CSS (web with Vite), NativeWind (React Native)
- **Component library:** Shared `packages/ui` — headless primitives + Aura-branded design tokens
- **Design tokens:** color palette, spacing scale, typography scale, elevation, border radius — stored in `packages/design-tokens`
- **State:** Jotai for local UI state; TanStack Query for server state and caching (complementary to Router's data fetching)

---

## Core Screens

### 1. Home Feed

Personalized recommendation cards in a scrollable feed. Each card shows:

- Product image + brand + name
- Primary recommendation reason (e.g., "Matches your warm undertone")
- Confidence indicator (high / medium / low)
- Quick-save button
- Shade swatch if applicable

**Empty state / cold start:** Onboarding prompt when profile is incomplete; shows popular items with a note that recommendations improve as profile fills in.

---

### 2. Product Detail

Detailed intelligence panel for a single product. Sections:

- **Shade selector** — shows all shades with compatibility badge for user's profile; best match highlighted
- **Ingredient list** — expandable; allergen-flagged ingredients highlighted in red; safe key actives highlighted in green
- **Recommendation analysis** — full reason code breakdown with evidence (e.g., "Niacinamide: compatible with dry skin")
- **Community sentiment** — sentiment score, top positive and cautionary themes, review count by skin type
- **Purchase options** — price comparison across merchants; affiliate label shown on any commissioned link
- **Outcome reporting** — post-purchase / post-use actions for "shade matched", "too light", "too dark", "caused irritation", and "returned / not keeping"
- **Similar products** — sidebar of products with similarity ≥ 0.75 ("Possible dupes")

---

### 3. Compare Products

Side-by-side comparison of 2–4 products. Per-product columns show:

| Row                         | Content                          |
| --------------------------- | -------------------------------- |
| Image + Brand + Name        | Visual identity                  |
| Overall compatibility score | Summary score for this user      |
| Shade match                 | Best matching shade + score      |
| Ingredient safety           | Safe / Alert badges              |
| Sentiment score             | Profile-filtered community score |
| Price                       | Current price range with links   |
| Quick-add to saved          | One-tap save action              |

**Key UX principle:** Differences between products are emphasized visually to speed up decision-making.

---

### 4. Profile Builder (You Index Dashboard)

Users set and review their profile attributes. Sections:

- **Beauty Profile** — skin type, undertone, skin tone, skin concerns, allergies (with add/remove)
- **Style Profile** — style archetype (visual selector), color preferences
- **Lifestyle & Ethical Preferences** — ethical filters (cruelty-free, vegan, etc.), spending range
- **Inferred Attributes** — shows what Aura has inferred from behavior; each attribute has an override option
- **Consent Center link** — prominent link to data and consent management

**Visual language:** Declared attributes shown with a solid badge; inferred attributes shown with a dotted badge + "Aura inferred" label.

---

### 5. Ask Aura — AI Assistant Interface

Conversational recommendation interface for complex, multi-step queries.

**Example queries the system handles:**

- "Find fragrance-free moisturizers under $40 for dry skin"
- "Compare [Product A] and [Product B] for my skin type"
- "What's a good budget dupe for [premium product]?"
- "I'm going on a beach vacation — what SPFs would work for my undertone?"

**UX pattern:**

- Chat-style input + structured results card
- Each result links back to full Product Detail page
- Conversation history available within the session
- Structured filters can be applied mid-conversation

---

### 6. Saved Items & Routines

A curated shelf of user-saved products. Features:

- Group by category (skincare, makeup, etc.)
- "Add to routine" action → triggers Routine Builder
- Compare saved items (shortcut to Compare screen)
- Mark outcome: kept, returned, shade mismatch, or reaction reported
- View similar products to a saved item

---

### 7. Consent & Privacy Center

Users manage all data and integration settings in one place.

Sections:

- **Active consents** — list of all granted integrations with revoke button
- **Data I've declared** — view and edit any declared profile attribute
- **Data Aura inferred** — view and override or delete any inferred attribute
- **Download my data** — export all profile and interaction data as JSON
- **Delete my account** — full self-serve account deletion with data purge

**Principle:** Every action in the consent center is immediate and irreversible (except re-granting). Aura does not put up friction for data deletion.

---

## Navigation Structure

```
Home (Feed)
├── Product Detail
│   ├── Compare
│   └── Similar Products
├── Search
├── Profile (You Index)
│   ├── Edit Profile
│   └── Consent Center
├── Saved
│   └── Routines
└── Ask Aura (AI Assistant)
```

---

## Mobile-Specific Considerations

- Bottom tab navigation for Home, Search, Saved, Profile, and Ask Aura
- Swipe gestures on recommendation cards: right to save, left to dismiss
- Shade swatch carousel for touch-friendly shade browsing
- Native share sheet for sharing product analysis links to social platforms

---

## Accessibility

- All interactive elements have descriptive ARIA labels (web) and accessibility labels (mobile)
- Minimum touch target size: 44×44px
- Color contrast meets WCAG AA for all text
- Screen reader announced state changes on saves, dismissals, and filters
- Ingredient safety alerts surfaced via text + color + icon (not color alone)
