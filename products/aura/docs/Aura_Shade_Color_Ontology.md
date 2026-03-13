# Aura Shade & Color Ontology

## Purpose

Standardize cosmetic shade descriptions across brands to enable cross-brand shade matching and recommendation. Brands name shades inconsistently ("Golden Beige", "Warm Sand", "W30"), making direct comparison impossible. The Aura Shade Ontology creates a universal mapping layer.

---

## Canonical Dimensions

Every shade in the ontology is described by four orthogonal dimensions:

| Dimension           | Values                       | Description                                                                  |
| ------------------- | ---------------------------- | ---------------------------------------------------------------------------- |
| **Skin Tone Depth** | 1 (Porcelain) → 10 (Deep)    | Numeric depth scale; mapped to industry-standard ranges                      |
| **Undertone**       | Cool, Warm, Neutral, Olive   | Pink/blue (cool), yellow/peach (warm), beige (neutral), green-golden (olive) |
| **Finish**          | Matte, Satin, Dewy, Luminous | Light-reflection quality of the formula                                      |
| **Coverage**        | Sheer, Light, Medium, Full   | Opacity and buildability                                                     |

---

## Skin Tone Depth Scale

This scale is a **product-matching aid only**. It is not a medical or dermatological skin classification and should not be treated as a substitute for the Fitzpatrick scale.

| Level | Common Descriptors   |
| ----- | -------------------- |
| 1     | Porcelain, Alabaster |
| 2     | Fair, Ivory          |
| 3     | Light, Shell         |
| 4     | Light-Medium, Beige  |
| 5     | Medium, Sand         |
| 6     | Medium-Tan, Golden   |
| 7     | Tan, Caramel         |
| 8     | Deep Tan, Cocoa      |
| 9     | Deep, Espresso       |
| 10    | Rich, Ebony          |

---

## Undertone Classification

| Undertone   | Visual Cues                      | Vein Color            | Works Best With               |
| ----------- | -------------------------------- | --------------------- | ----------------------------- |
| **Cool**    | Pink, rosy, or bluish hue        | Blue/purple           | Silver jewelry, jewel tones   |
| **Warm**    | Yellow, peachy, or golden hue    | Green                 | Gold jewelry, earth tones     |
| **Neutral** | Mix of warm and cool, true beige | Blue-green            | Both silver and gold          |
| **Olive**   | Green or yellow-green cast       | Green with olive tint | Warm-toned, earthier palettes |

---

## Example Cross-Brand Shade Mapping

| Brand   | Shade Name     | Canonical Depth | Undertone | Finish |
| ------- | -------------- | --------------- | --------- | ------ |
| Brand A | "Golden Beige" | 5               | Warm      | Satin  |
| Brand B | "Warm Honey"   | 5               | Warm      | Satin  |
| Brand C | "W30"          | 5               | Warm      | Matte  |
| Brand D | "Sand Warm"    | 5               | Warm      | Dewy   |

All four map to **Depth 5, Warm undertone** — enabling Aura to recommend any as cross-brand alternatives.

---

## Shade Matching Algorithm

Given a user's skin tone depth and undertone, shade compatibility is scored as:

```
shade_score = undertone_match_weight × undertone_score
            + depth_match_weight × depth_proximity_score
```

Where:

- `undertone_score` = 1.0 (exact match), 0.7 (adjacent: Warm ↔ Neutral), 0.3 (opposite: Cool ↔ Warm)
- `depth_proximity_score` = 1.0 - (|user_depth - shade_depth| / 10)
- `undertone_match_weight` = 0.65, `depth_match_weight` = 0.35

Finish and coverage are not included in the compatibility score — they are presented as user-filterable preferences.

---

## Ontology Maintenance

- New product shades ingested through the catalog pipeline are mapped to the canonical dimensions by the Enrichment Worker.
- Shades that cannot be confidently mapped (insufficient metadata) are stored with a `LOW_CONFIDENCE` flag and excluded from shade-match scoring until manually reviewed.
- The ontology is versioned. Recommendation reason codes cite the ontology version used for the shade match.
- User feedback ("this shade didn't match me") is collected and used to audit and refine mappings over time.

---

## Usage in Recommendations

When generating shade-related recommendations:

1. Shade Matching Agent queries all shades for the candidate product.
2. Each shade is scored against the user's skin tone and undertone.
3. The best-matching shade is highlighted in the recommendation card.
4. If no shade scores above threshold (0.5), a `LOW_SHADE_CONFIDENCE` trust flag is added.
5. The reason code `SHADE_MATCH` is attached when the best shade scores ≥ 0.75.
