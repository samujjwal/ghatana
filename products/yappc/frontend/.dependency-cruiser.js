/**
 * Dependency Cruiser Configuration
 * Phase E.1 - Governance Rules for Canvas Library
 *
 * Enforces architectural boundaries between apps and libs
 */

module.exports = {
  forbidden: [
    {
      name: 'no-apps-imports-in-libs',
      severity: 'error',
      comment:
        'Libraries should not import from application code - violates architectural boundaries',
      from: {
        path: '^libs/',
      },
      to: {
        path: '^apps/',
        pathNot: '^node_modules',
      },
    },
    {
      name: 'no-examples-in-production',
      severity: 'warn',
      comment:
        'Canvas examples should only be imported in development builds or tests',
      from: {
        path: '^(apps|libs)/',
        pathNot: '(test|spec|\\.stories\\.|storybook|examples/)',
      },
      to: {
        path: '^libs/canvas/examples/',
      },
    },
  ],
  allowed: [
    {
      from: {
        path: '^apps/',
      },
      to: {
        path: '^libs/',
      },
    },
    {
      from: {
        path: '^libs/',
      },
      to: {
        path: '^libs/',
      },
    },
    {
      // Allow internal imports within same lib
      from: {
        path: '^libs/([^/]+)/',
      },
      to: {
        path: '^libs/$1/',
      },
    },
  ],
  options: {
    /* conditions of when dependency-cruiser should cry */
    doNotFollow: {
      /* don't blacklist node_modules, just exclude them from analysis */
      path: 'node_modules',
    },

    /* conditions of which files not to cruise */
    exclude: {
      /* pattern specifying which files not to cruise */
      path: '(node_modules|dist/|build/|\\.d\\.ts$|\\.js\\.map$)',
    },

    /* conditions of which dependencies to exclude */
    includeOnly: {
      /* pattern specifying which files to include (on top of exclude) */
      path: '\\.(js|ts|jsx|tsx)$',
    },

    /* dependency-cruiser will include modules matching this pattern 
       in its output, as well as their neighbours (direct dependencies
       and dependents) */
    focus: '',

    /* list of module systems to cruise */
    /* All of these module systems are supported, but it's probably
       useful to put in your own weight restrictions */
    moduleSystems: ['amd', 'cjs', 'es6', 'tsd'],

    /* prefix for links in html and svg output (e.g. 'https://github.com/you/yourrepo/blob/develop/'
       to open it on your online repo or `vscode://file/${process.cwd()}/` to 
       open it in visual studio code),
     */
    prefix: `vscode://file/\${process.cwd()}/`,

    /* false (the default): ignore dependencies that only exist before typescript-to-javascript compilation
       true: also detect dependencies that only exist before typescript-to-javascript compilation
       "specify": for each dependency identify whether it only exists before compilation or also after
     */
    preserveSymlinks: false,

    /* if true combines the package.jsons found from the module up to the base
       folder the cruise is initiated from. Useful for how (some) mono-repos
       manage dependencies & dependency definitions.
     */
    combinedDependencies: false,

    /* if true leave symlinks untouched, otherwise use the realpath */
    preserveSymlinks: false,

    /* TypeScript project file ('tsconfig.json') to use for
       (1) compilation and
       (2) resolution (e.g. with the paths property)
       
       The (optional) fileName attribute specifies which file to take (relative to
       dependency-cruiser's current working directory). When not provided
       defaults to './tsconfig.json'.
     */
    tsConfig: {
      fileName: 'tsconfig.json',
    },

    /* Webpack resolve options to use when resolving modules
       dependency-cruiser currently supports:
       - the 'alias' property
       - the 'modules' property. In a monorepo this can be useful
         (see above)
    */
    webpackConfig: {
      /* ... */
    },

    /* How to resolve External dependencies - use this when you want
       to keep external dependencies in the output but want to use
       something else than node_modules/package.json for their version numbers
    */
    externalModuleResolutionStrategy: 'node_modules',

    /* List of strings you have in use in addition to cjs/ es6 requires
       & imports to declare module dependencies. Use this e.g. if you've
       redeclared require (`const want = require`), use a require-wrapper
       (like `const resolve = require(require.resolve`). Or if you have
       case insensitive file systems - and use different cases to reference the same
       module. Really, you might want to improve your code first, but this
       allows you to map it nonetheless.
     */
    exoticRequireStrings: [],

    /* options to pass on to enhanced-resolve, the package dependency-cruiser
       uses to resolve module references to disk. You can set most of these
       options in a webpack.config.js - this section is here for those
       projects that don't have a separate webpack config file.

       Note: settings in webpack.config.js override the ones specified here.
     */
    enhancedResolveOptions: {
      /* List of strings to consider as 'exports' fields in package.json. Use
         ['exports'] when you use packages that use such a field and your environment
         supports it (e.g. node ^12.19 || >=14.7 or recent versions of webpack).
      
         If you have an `exportsFields` attribute in your webpack config, that one
         will have precedence over the one specified here.
      */
      exportsFields: ['exports'],
      /* List of conditions to check for in the exports field. e.g. use ['imports']
         if you're only interested in exposed es6 modules, ['require'] for commonjs,
         or all conditions at once `(['import', 'require', 'node', 'default']`)
         if anything goes for you. Only works when the 'exportsFields' array is
         non-empty.
        
         If you have a 'conditionNames' attribute in your webpack config, that one will
         have precedence over the one specified here.
      */
      conditionNames: ['import', 'require', 'node', 'default'],
    },

    reporterOptions: {
      dot: {
        /* pattern of modules that can be consolidated in the detailed
           graphical dependency graph. The default pattern in this configuration
           collapses everything in node_modules to one folder deep so you see
           the external modules, but their innards.
         */
        collapsePattern: 'node_modules/[^/]+',

        /* Options to tweak the appearance of your graph.See
           https://www.graphviz.org/doc/info/attrs.html for details.
           Note: certain themes override certain attributes.
         */
        theme: {
          graph: {
            bgcolor: 'dodgerblue',
            color: 'white',
            fontcolor: 'white',
            fillcolor: 'transparent',
          },
          modules: [
            {
              criteria: { source: '^src/model' },
              attributes: { fillcolor: 'lightblue' },
            },
          ],
        },
      },
      archi: {
        /* pattern of modules that can be consolidated in the high level
           graphical dependency graph. If you use the high level graphical
           dependency graph reporter (`archi`) you probably want to tweak
           this collapsePattern to your situation.
         */
        collapsePattern: '^(packages|src|lib|app)/[^/]+|node_modules/[^/]+',
      },
      text: {
        highlightFocused: true,
      },
    },
  },
};
