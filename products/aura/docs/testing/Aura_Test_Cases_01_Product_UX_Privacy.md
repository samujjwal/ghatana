# Aura Test Cases 01: Product, UX, Privacy, and Accessibility

Version: 1.0
Date: March 13, 2026

## Scope

This suite covers the user-visible Aura experience:

- onboarding and profile creation
- feed and recommendation cards
- product detail and comparison
- Ask Aura assistant
- saved items and outcome reporting
- consent center, export, deletion
- accessibility and mobile interactions

## Case Format

Each case is intentionally TDD-friendly:

- `Level`: the first automation target
- `Priority`: `P0`, `P1`, or `P2`
- `Source Docs`: where the requirement originates
- `Expected`: assertions that should become automated checks

---

## A. Onboarding and Core Profile

### AURA-PUX-001 Core onboarding succeeds without optional consents
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: New authenticated user with no consents.
Steps:
1. Open onboarding.
2. Enter skin type, undertone, skin tone, skin concerns, allergies, ethical preferences, and spending range.
3. Decline optional selfie, receipt import, wearable, and community-sharing prompts if shown.
4. Submit onboarding and open the first feed.
Expected:
- Onboarding completes without requiring optional consent.
- Declared fields persist as `DECLARED`.
- Feed is available immediately after onboarding.
- No optional integration scope is marked active.

### AURA-PUX-002 Onboarding blocks invalid required profile combinations
Level: Integration
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_API_Contracts.md`
Preconditions: New authenticated user.
Steps:
1. Submit onboarding with missing required supported beauty fields.
2. Submit onboarding with malformed arrays, duplicate allergies, or invalid spending ranges.
Expected:
- Validation errors are field-specific and actionable.
- No partial invalid profile is persisted.
- Error payload follows API contract shape.

### AURA-PUX-003 Cold-start user sees non-personalized feed messaging
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_AI_Engine_Design.md`, `Aura_Personal_Intelligence_Engine_Spec.md`
Preconditions: Authenticated user with no declared profile.
Steps:
1. Open home feed.
2. Inspect recommendation card language and confidence display.
Expected:
- Feed contains popular fallback recommendations only.
- UI explicitly says personalization is limited.
- Low-confidence or limited-data language is visible.

### AURA-PUX-004 Partial profile produces medium-confidence recommendations
Level: E2E
Priority: P1
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`, `Aura_UI_UX_Blueprint.md`
Preconditions: User declares only skin type and allergies.
Steps:
1. Request recommendations for supported skincare.
2. Inspect feed and product detail confidence language.
Expected:
- Recommendations are generated.
- Confidence is reduced relative to a complete profile.
- Prompt encourages profile completion without blocking use.

### AURA-PUX-005 Complete profile produces high-confidence supported flows
Level: E2E
Priority: P1
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`, `Aura_Recommendation_Algorithms.md`
Preconditions: User has complete complexion and skincare profile.
Steps:
1. Request foundation and moisturizer recommendations.
2. Inspect returned cards and detail views.
Expected:
- Confidence labels are high when product data is complete.
- Reason codes and evidence align with full-profile signals.

### AURA-PUX-006 Style archetype onboarding supports primary and secondary selections
Level: Integration
Priority: P2
Source Docs: `Aura_Style_Archetype_Taxonomy.md`, `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`
Preconditions: User in style profile onboarding or edit flow.
Steps:
1. Complete style quiz with answers that split across two archetypes.
2. Save the result.
Expected:
- Primary and secondary archetype values persist.
- Profile edit screen reflects both.
- Recommendation requests can read the updated style profile.

---

## B. Profile Transparency, Inference, and Overrides

### AURA-PUX-007 Inferred attributes are visually distinct from declared attributes
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_UI_UX_Blueprint.md`
Preconditions: User has at least one inferred attribute.
Steps:
1. Open profile builder.
2. Compare declared and inferred attribute sections.
Expected:
- Declared, inferred, and imported data are visually separated.
- Inferred attributes show confidence or origin metadata.

### AURA-PUX-008 User can override an inferred attribute
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: User has inferred spending preference or style attribute.
Steps:
1. Override the inferred value from profile builder.
2. Refresh profile and fetch recommendation history.
Expected:
- Override persists as user-controlled state.
- Profile attribute origin/history is updated.
- A profile override event is emitted.
- Subsequent recommendations use the override.

### AURA-PUX-009 User can delete an inferred attribute without damaging declared data
Level: Integration
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`
Preconditions: User has both declared and inferred attributes for same domain.
Steps:
1. Delete the inferred attribute.
2. Reload profile.
Expected:
- Only inferred value is removed.
- Declared values remain intact.
- Confidence and recommendation behavior recalculate safely.

### AURA-PUX-010 Imported attributes disappear after consent revocation
Level: E2E
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: User has active optional import consent and imported data.
Steps:
1. Revoke the relevant consent scope.
2. Reload profile and recommendation surfaces.
Expected:
- Imported attributes stop influencing recommendations.
- Imported attribute section is removed or marked inactive.
- Consent revocation event is emitted.

### AURA-PUX-011 Profile completeness recalculates after edits
Level: Unit
Priority: P1
Source Docs: `Aura_API_Contracts.md`, `Aura_Personal_Intelligence_Engine_Spec.md`
Preconditions: Profile completeness algorithm available.
Steps:
1. Create incomplete, partial, and complete supported beauty profiles.
2. Edit fields across each state.
Expected:
- Completeness score changes deterministically.
- Missing supported fields reduce confidence as documented.

### AURA-PUX-012 Unsupported optional high-sensitivity features remain optional everywhere
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_Data_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: User declines all high-sensitivity scopes.
Steps:
1. Use core product flows for feed, compare, saved items, and export.
Expected:
- No core screen is blocked.
- No dark-pattern re-prompting occurs.
- Product remains usable for core supported journeys.

---

## C. Feed and Recommendation Cards

### AURA-PUX-013 Recommendation card always shows a primary reason
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`
Preconditions: Recommendations available.
Steps:
1. Open feed with multiple recommendations.
2. Inspect each card.
Expected:
- Every card exposes at least one primary reason.
- Empty or blank reason areas never render.

### AURA-PUX-014 Recommendation card shows confidence state and trust flags
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_Recommendation_Algorithms.md`
Preconditions: Feed contains mixed-confidence results.
Steps:
1. Inspect high-, medium-, and low-confidence cards.
2. Trigger stale source, low shade confidence, and partial ingredient scenarios.
Expected:
- Confidence state is visible.
- Trust flags are readable and not hidden behind tooltips only.
- Flag wording matches returned contract values.

### AURA-PUX-015 Quick-save action is idempotent
Level: Integration
Priority: P1
Source Docs: `Aura_PRD_v1.md`, `Aura_API_Contracts.md`
Preconditions: Same product rendered more than once across surfaces.
Steps:
1. Save product from feed.
2. Save same product again from product detail and compare.
Expected:
- Only one saved record exists.
- UI resolves into saved state everywhere.
- No duplicate feedback or owned-state anomalies occur.

### AURA-PUX-016 Dismiss action suppresses near-term repetition
Level: E2E
Priority: P1
Source Docs: `Aura_Personal_Intelligence_Engine_Spec.md`, `Aura_Recommendation_Algorithms.md`
Preconditions: User sees recommendation cluster with similar products.
Steps:
1. Dismiss a product.
2. Refresh feed and request again in same session.
Expected:
- Exact dismissed item is removed from near-term results.
- Similar items are penalized according to negative preference logic.

### AURA-PUX-017 Affiliate labeling appears wherever purchase intent is shown
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_GTM_Strategy.md`
Preconditions: Recommendation with at least one affiliate merchant source.
Steps:
1. Open feed card CTA.
2. Open product detail purchase options.
3. Open compare surface purchase links.
Expected:
- Affiliate links are explicitly labeled.
- Non-affiliate links are not mislabeled.
- Ranking order does not change when affiliate data is toggled.

---

## D. Product Detail, Compare, and Search

### AURA-PUX-018 Product detail shows evidence-backed recommendation analysis
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_AI_Engine_Design.md`
Preconditions: Supported product detail page.
Steps:
1. Open a recommended product detail page.
2. Inspect recommendation analysis section.
Expected:
- Evidence references include ingredient, shade, or community facts.
- Evidence matches structured contract payload.
- No unsupported claims appear.

### AURA-PUX-019 Product detail highlights ingredient alerts and safe actives correctly
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_Ingredient_Knowledge_Graph.md`
Preconditions: Product contains both safe actives and user-relevant alerts.
Steps:
1. Open ingredient list.
2. Expand detail rows for flagged ingredients.
Expected:
- Alerting ingredients are visibly differentiated from safe actives.
- Reason text names the relevant ingredient or class.

### AURA-PUX-020 Shade selector highlights best match and low-confidence cases
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_Shade_Color_Ontology.md`
Preconditions: Supported complexion product with multiple shades.
Steps:
1. Open shade selector for well-mapped product.
2. Open shade selector for low-metadata product.
Expected:
- Best match is highlighted when threshold is met.
- Low-confidence warning appears when metadata is incomplete or scores are low.

### AURA-PUX-021 Compare supports minimum and maximum item counts
Level: E2E
Priority: P1
Source Docs: `Aura_PRD_v1.md`, `Aura_UI_UX_Blueprint.md`
Preconditions: Comparable supported products available.
Steps:
1. Compare 2, 3, and 4 products.
2. Attempt compare with 1 and 5 products.
Expected:
- 2 to 4 item compare works.
- Invalid compare counts are rejected with clear messaging.

### AURA-PUX-022 Compare emphasizes differences for user-specific signals
Level: E2E
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`
Preconditions: Compared products differ on price, ingredient safety, and shade match.
Steps:
1. Open compare grid.
2. Inspect row highlighting.
Expected:
- Important deltas are visually emphasized.
- User-specific compatibility differences are clearer than generic catalog differences.

### AURA-PUX-023 Search respects category, price, and ethical filters
Level: Integration
Priority: P1
Source Docs: `Aura_API_Contracts.md`, `Aura_PRD_v1.md`
Preconditions: Search index populated.
Steps:
1. Search with no filters.
2. Search with category, `priceMax`, and ethical filters.
Expected:
- Results narrow correctly.
- Unsupported or malformed filters are rejected.

---

## E. Ask Aura and Saved Items

### AURA-PUX-024 Ask Aura handles structured shopping query end-to-end
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_AI_Agent_Architecture.md`
Preconditions: User with declared dry skin and fragrance allergy.
Steps:
1. Ask: "Find fragrance-free moisturizers under $40 for dry skin."
2. Inspect returned result cards.
Expected:
- Results honor category, price, and allergen constraints.
- Assistant returns structured product cards, not free-form unsupported advice.

### AURA-PUX-025 Ask Aura compare query links to compare surface
Level: E2E
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`
Preconditions: Two supported products available.
Steps:
1. Ask Aura to compare product A and product B.
2. Open linked compare view.
Expected:
- Same products and scores appear in compare view.
- No query-to-UI mismatch occurs.

### AURA-PUX-026 Ask Aura preserves mid-conversation structured filters
Level: E2E
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: Existing assistant conversation in session.
Steps:
1. Run a base query.
2. Add a new budget or ethical constraint mid-conversation.
Expected:
- Refined results honor the new constraint.
- Conversation context remains in session.

### AURA-PUX-027 Saved items support outcome reporting
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: Saved product exists.
Steps:
1. Open saved items.
2. Mark item as kept, returned, shade mismatch, or reaction reported.
Expected:
- Outcome is accepted through the saved-items surface.
- Result is persisted and evented.
- Recommendation history reflects the outcome.

### AURA-PUX-028 Saved items compare shortcut preserves source state
Level: Integration
Priority: P2
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: At least two saved items exist.
Steps:
1. Launch compare from saved items.
2. Return back to saved items.
Expected:
- Compare opens with selected items only.
- Back navigation preserves saved-item state and filters.

---

## F. Consent Center and Data Rights

### AURA-PUX-029 Consent center lists active scopes only
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`
Preconditions: User has mixed granted and revoked scopes.
Steps:
1. Open consent center.
2. Inspect active consent list.
Expected:
- Only currently granted scopes appear in active list.
- Revoked scopes are clearly represented as inactive or historical.

### AURA-PUX-030 Granting optional scope updates profile behavior only for that scope
Level: E2E
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: User has no optional consents.
Steps:
1. Grant one optional scope such as selfie analysis.
2. Leave all other scopes denied.
Expected:
- Only that scope is activated.
- No unrelated imported or high-sensitivity flows turn on.

### AURA-PUX-031 Revoking scope removes its effect without deleting unrelated user data
Level: E2E
Priority: P0
Source Docs: `Aura_Data_Architecture.md`, `Aura_API_Contracts.md`
Preconditions: User has multiple optional scopes active.
Steps:
1. Revoke a single scope.
2. Inspect profile and recommendation behavior.
Expected:
- Only data from that scope stops contributing.
- Core service data remains intact.

### AURA-PUX-032 Data export request succeeds for mature account
Level: E2E
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`, `Aura_API_Contracts.md`, `Aura_Event_Architecture.md`
Preconditions: User has profile, recommendations, feedback, and outcomes.
Steps:
1. Request data export.
2. Poll export status.
Expected:
- Export request is created.
- Status transitions are visible.
- Export event is emitted.
- Result contains declared, inferred, imported, interaction, and recommendation history.

### AURA-PUX-033 Account deletion requires re-authentication and completes data purge flow
Level: E2E
Priority: P0
Source Docs: `Aura_PRD_v1.md`, `Aura_API_Contracts.md`, `Aura_Data_Architecture.md`
Preconditions: Mature user account with saved items, outcomes, and consents.
Steps:
1. Initiate deletion without re-authentication.
2. Re-attempt with required re-authentication.
3. Attempt to access deleted account data.
Expected:
- First attempt is rejected safely.
- Confirmed deletion succeeds.
- Subsequent access to user-owned data is denied.
- Purge workflow and audit trail are recorded.

---

## G. Accessibility and Mobile

### AURA-PUX-034 Feed cards are screen-reader navigable
Level: Non-Functional
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: Web client with screen reader enabled.
Steps:
1. Tab through recommendation cards.
2. Trigger save and dismiss actions.
Expected:
- Card reason, confidence, and action labels are announced.
- State changes are announced after save and dismiss.

### AURA-PUX-035 Ingredient alerts do not rely on color alone
Level: Non-Functional
Priority: P0
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: Product detail page with ingredient alerts.
Steps:
1. Inspect alert state in normal, grayscale, and high-contrast modes.
Expected:
- Text and icon semantics remain understandable without color.

### AURA-PUX-036 Mobile swipe gestures map to correct actions
Level: E2E
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: Mobile client with recommendation cards.
Steps:
1. Swipe right on a card.
2. Swipe left on a card.
Expected:
- Right swipe saves.
- Left swipe dismisses.
- Undo or state feedback is visible and accessible.

### AURA-PUX-037 Minimum tap target size is respected on critical controls
Level: Non-Functional
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: Mobile and responsive web layouts.
Steps:
1. Measure save, compare, filter, consent, and delete-account controls.
Expected:
- Critical controls meet minimum target size requirements.

### AURA-PUX-038 Responsive layouts preserve trust signals
Level: E2E
Priority: P1
Source Docs: `Aura_UI_UX_Blueprint.md`
Preconditions: Same recommendation displayed on desktop and mobile widths.
Steps:
1. Compare card and detail layouts across breakpoints.
Expected:
- Reason text, confidence, trust flags, and affiliate labels remain visible on all supported breakpoints.
