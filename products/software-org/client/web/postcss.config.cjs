/**
 * PostCSS configuration for Tailwind CSS v4 and autoprefixer.
 *
 * Tailwind v4 uses the '@tailwindcss/postcss' plugin which automatically
 * includes base, components, and utilities. The theme and content options
 * are configured in tailwind.config.ts.
 *
 * @see tailwind.config.ts for theme and content configuration
 */
module.exports = {
    plugins: {
        '@tailwindcss/postcss': {},
        autoprefixer: {},
    },
};
