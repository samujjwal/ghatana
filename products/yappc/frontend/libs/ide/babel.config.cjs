module.exports = function (api) {
    api.cache(true);
    return {
        presets: [
            ['@babel/preset-env', { targets: { node: '16' }, modules: 'auto' }],
            ['@babel/preset-typescript', { allowNamespaces: true }],
            ['@babel/preset-react', { runtime: 'automatic', importSource: 'react' }],
        ],
        plugins: [],
    };
};
