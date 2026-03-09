/**
 * PostCSS configuration for the monorepo
 * 
 * Processes Tailwind CSS directives and applies autoprefixer for browser compatibility.
 * 
 * @see {@link https://tailwindcss.com/docs/installation/using-postcss PostCSS with Tailwind}
 */
export default {
  plugins: {
    '@tailwindcss/postcss': {},
    autoprefixer: {},
  },
};
