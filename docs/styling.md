# Shared UI styling

All screens belong inside `SendItTheme`, already applied in `MainActivity`.
It uses Compose Material 3's colour, typography, and shape system within the UML UI layer.

The supplied attempt-detail PNG is the visual reference. Colours, spacing, and type sizes
are approximate; the exact font was not provided, so the theme uses Android's system
sans-serif. The current design is dark regardless of device theme or wallpaper colours.
The activity uses light system-bar icons to suit this background.

## Files in ui/theme

- `Color.kt`: palette values; edit these to adjust colours globally.
- `Theme.kt`: maps colours to semantic Material roles and applies the theme.
- `Type.kt`: shared text styles, using scalable sp sizes.
- `Shape.kt`: shared corner radii.
- `Spacing.kt`: shared layout padding and gaps, using dp sizes.

## Using the theme

Use `MaterialTheme` roles in screens instead of repeating raw colours and font sizes:

| Purpose | Theme value |
| --- | --- |
| Page background | `MaterialTheme.colorScheme.background` |
| Card background | `MaterialTheme.colorScheme.surface` |
| Raised card or row | `MaterialTheme.colorScheme.surfaceContainerHigh` |
| Primary action | `MaterialTheme.colorScheme.primary` |
| Text on primary action | `MaterialTheme.colorScheme.onPrimary` |
| Peach accent | `MaterialTheme.colorScheme.secondary` |
| Main text | `MaterialTheme.colorScheme.onSurface` |
| Supporting text | `MaterialTheme.colorScheme.onSurfaceVariant` |
| Page heading | `MaterialTheme.typography.headlineSmall` |
| Section heading | `MaterialTheme.typography.titleLarge` |
| Body text | `MaterialTheme.typography.bodyLarge` |
| Card corners | `MaterialTheme.shapes.large` |
| Button corners | `MaterialTheme.shapes.small` |
| Page padding | `SendItSpacing.screenPadding` |
| Section gap | `SendItSpacing.extraLarge` |

For example, inside a composable:

```kotlin
Text(
    text = "Techniques used",
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onSurface
)
```

Material components inherit the theme, but each component chooses its own default role.
For a card matching the design, explicitly use `shape = MaterialTheme.shapes.large`
and `colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`.
For a primary button matching the reference corners, use `shape = MaterialTheme.shapes.small`.
Wrap Compose previews in `SendItTheme` too.

## Current assignment scope

The assignment implements the ML video-analysis feature and its results, with a minimal
separate video-import screen supplying input. The full designed attempt form and route
vault are out of scope. Keep UML component responsibilities and names for the implemented
subset. Comparison and other-attempt UI remain deferred. The results screen must not
contain video import.

The starter app has run successfully on the user's phone. This increment establishes
only shared styling; video import, Room, and ML implementation remain separate checkpoints.
Use commit messages in the form `type: short description`, such as `feat:`, `style:`,
`refactor:`, `fix:`, `build:`, or `docs:`. The user makes their own commits and pushes.
