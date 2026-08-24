# Seewik design reference

## Primary reference

Seewik's visual system is based on the NagrikLoop civic prototype supplied as the product-design reference on 2026-08-24.

Use the prototype as the first reference for future citizen-flow screens, especially its mobile proportions, one-decision-per-screen principle, status treatments, cards, controls, and colour roles. Do not copy prototype functionality into Seewik unless it is separately included in the product plan.

## Colour roles

- Navy `#0E2340`: trust, primary text, and primary actions.
- Teal `#0D8F7B`: confirmation, completion, successful states, and links.
- Saffron `#D9702A`: the civic-improvement journey, progress, and attention states.
- Warm ground `#F6F4EF` and wall `#EDE9E1`: page backgrounds.
- White `#FFFFFF` and warm white `#FBF9F5`: cards and inset surfaces.
- Muted blue-grey `#65718A`: supporting text.
- Danger red `#C0392B`: genuine errors only.

The matching dark palette is defined in `frontend/src/styles.css` and follows the prototype's automatic system-theme treatment.

## Typography and layout

- Display type: Bricolage Grotesque.
- Body type: Figtree, with Noto Sans Devanagari for Marathi and other Devanagari text.
- Minimum primary control height: 52px.
- Cards: quiet borders, restrained shadows, and 18–24px corner radii.
- Prefer one clear decision per section and keep confirmation states visually distinct from warnings and errors.

## Implementation

All reusable design tokens live at the top of `frontend/src/styles.css`. New UI should use those variables instead of adding isolated colour values.
