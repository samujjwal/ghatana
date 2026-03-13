# Aura Style Archetype Taxonomy

## Purpose

Classify fashion and beauty preferences into recognizable style archetypes to enable style-aware recommendations, routine personalization, and wardrobe intelligence (Phase 3+). Archetypes provide a compressed, stable representation of aesthetic preferences that is easier to elicit and reason over than raw preference vectors.

---

## Core Archetypes

| Archetype      | Description                         | Color Palette                       | Silhouettes                   | Beauty Aesthetic                   |
| -------------- | ----------------------------------- | ----------------------------------- | ----------------------------- | ---------------------------------- |
| **Minimalist** | Clean, understated, quality-driven  | Neutrals: white, black, grey, beige | Simple, structured            | No-makeup makeup, skincare-first   |
| **Classic**    | Timeless, polished, sophisticated   | Navy, ivory, camel, burgundy        | Tailored, traditional         | Defined brows, red lip, refined    |
| **Bohemian**   | Free-spirited, earthy, eclectic     | Terracotta, rust, sage, cream       | Flowy, layered, relaxed       | Natural, sun-kissed, braided       |
| **Streetwear** | Urban, bold, trend-forward          | Black, white, primary pops          | Oversized, graphic, athletic  | Bold eye, glossy lip, edgy         |
| **Elegant**    | Refined, luxurious, feminine        | Blush, champagne, pearl, plum       | Draped, fitted, flowing       | Glam, sculptural, eveningwear      |
| **Athleisure** | Active, comfortable, functional     | Athletic tones, neons, neutrals     | Performance-fit, hybrid       | Fresh, dewy, minimal               |
| **Vintage**    | Nostalgic, curated, decade-inspired | Mustard, rust, olive, pastels       | A-line, retro cuts            | Pin-up, mod, era-specific          |
| **Edgy**       | Daring, unconventional, statement   | Monochrome, electric, dark          | Asymmetric, structured, sharp | Graphic eye, bold lip, avant-garde |
| **Romantic**   | Soft, feminine, dreamy              | Blush, lavender, dusty rose, cream  | Floral, ruffled, delicate     | Dewy skin, soft blush, floral      |
| **Preppy**     | Collegiate, clean, structured       | Pastel, navy, white, stripe         | Button-down, chino, blazer    | Fresh, natural, put-together       |

---

## Archetype Blends

Users rarely fit a single archetype. Aura supports **archetype blends**: a primary + secondary archetype pairing that reflects real-world aesthetic complexity.

| Example Blend           | Interpretation                                             |
| ----------------------- | ---------------------------------------------------------- |
| Minimalist + Classic    | Very refined, neutral palettes, timeless essentials only   |
| Bohemian + Romantic     | Soft, flowy, earth tones with floral and feminine details  |
| Streetwear + Edgy       | Urban boldness with statement, unconventional pieces       |
| Athleisure + Minimalist | Functional and clean — quality basics with sporty elements |

Blend weights are expressed as primary (60–70%) and secondary (30–40%) contributions to preference scoring.

---

## Onboarding Elicitation

During onboarding, users are shown a visual quiz:

1. **Style image cards** — rate 8–10 outfits from "love" to "not for me"
2. **Color palette selections** — choose palettes that feel like "you"
3. **Word association** — select 3–5 words that describe your ideal style

The responses are scored against archetype profiles to assign a primary and secondary archetype. Users can override or adjust their archetype at any time from the profile dashboard.

---

## Usage in Recommendations

| Context                   | How Archetype Is Used                                                                                                        |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Product recommendations   | Favors products aligned with the user's archetype aesthetic (shade palette, finish style)                                    |
| Feed curation             | Prioritizes content matching the user's visual style in inspiration panels                                                   |
| Shade suggestions         | Weights shade finishes and palettes by archetype preference (e.g., Minimalist → matte and nude; Edgy → dramatic, dark tones) |
| Routine builder (Phase 3) | Suggests routines that match lifestyle and aesthetic complexity level                                                        |

---

## Taxonomy Maintenance

- Archetypes are stable labels; new archetypes are added only when significant unmet user segments emerge.
- Sub-archetypes or facets (e.g., "Dark Academia" as a Vintage sub-type) can be added as secondary enrichment without breaking primary archetype logic.
- Archetype assignment is re-evaluated as user behavioral signals accumulate (no hard re-assignment without user confirmation).
