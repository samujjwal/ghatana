module.exports = {
  presets: [
    ['@babel/preset-env', { 
      targets: { node: 'current' },
      modules: 'commonjs',
    }],
    ['@babel/preset-react', { 
      runtime: 'automatic',
      importSource: '@emotion/react',
    }],
    ['@babel/preset-typescript', {
      allExtensions: true,
      isTSX: true,
      jsxPragma: 'React',
      allowDeclareFields: true,
    }],
  ],
  sourceType: 'unambiguous',
  plugins: [
    ['@babel/plugin-proposal-decorators', { legacy: true }],
    ['@babel/plugin-transform-class-properties', { loose: true }],
    ['@babel/plugin-transform-private-methods', { loose: true }],
    ['@babel/plugin-transform-private-property-in-object', { loose: true }],
    '@babel/plugin-transform-optional-chaining',
    '@babel/plugin-transform-nullish-coalescing-operator',
    'babel-plugin-styled-components',
    '@babel/plugin-transform-runtime',
  ],
  env: {
    test: {
      plugins: [
        '@babel/plugin-transform-runtime',
        '@babel/plugin-transform-modules-commonjs',
      ],
    },
  },
};
