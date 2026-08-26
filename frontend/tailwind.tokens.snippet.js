// Wire the design tokens into Tailwind so utilities resolve to the CSS variables
// in src/styles/tokens.css. Merge this into tailwind.config.js `theme.extend`.
// Goal: the agent uses `bg-primary text-ink font-display` etc. — NOT Tailwind's
// default palette (bg-emerald-600, text-slate-900), which would bypass the tokens.

module.exports = {
  theme: {
    extend: {
      colors: {
        ink: {
          DEFAULT: "var(--color-ink)",
          soft: "var(--color-ink-soft)",
          faint: "var(--color-ink-faint)",
        },
        surface: {
          DEFAULT: "var(--color-surface)",
          raised: "var(--color-surface-raised)",
          sunk: "var(--color-surface-sunk)",
        },
        border: "var(--color-border)",
        primary: {
          DEFAULT: "var(--color-primary)",
          hover: "var(--color-primary-hover)",
          contrast: "var(--color-primary-contrast)",
        },
        accent: {
          DEFAULT: "var(--color-accent)",
          soft: "var(--color-accent-soft)",
        },
        success: {
          DEFAULT: "var(--color-success)",
          soft: "var(--color-success-soft)",
        },
        danger: {
          DEFAULT: "var(--color-danger)",
          soft: "var(--color-danger-soft)",
        },
      },
      fontFamily: {
        display: "var(--font-display)",
        body: "var(--font-body)",
        mono: "var(--font-mono)",
      },
      fontSize: {
        xs: "var(--text-xs)",
        sm: "var(--text-sm)",
        base: "var(--text-base)",
        lg: "var(--text-lg)",
        xl: "var(--text-xl)",
        "2xl": "var(--text-2xl)",
        "3xl": "var(--text-3xl)",
        "4xl": "var(--text-4xl)",
      },
      borderRadius: {
        sm: "var(--radius-sm)",
        md: "var(--radius-md)",
        lg: "var(--radius-lg)",
        pill: "var(--radius-pill)",
      },
      boxShadow: {
        card: "var(--shadow-card)",
        pop: "var(--shadow-pop)",
      },
      maxWidth: {
        container: "var(--container-max)",
      },
    },
  },
  // Set the default body font on <html>/<body> in a base layer, and use
  // `font-variant-numeric: tabular-nums` on any element showing times/dates/distances.
};
