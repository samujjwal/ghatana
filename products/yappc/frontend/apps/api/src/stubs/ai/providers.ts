export interface ILLMProvider {
    generateEmbedding(text: string): Promise<number[]>;
    embed(text: string): Promise<number[]>;
}

export function createProviderFactory(config?: unknown) {
    return {
        create: (name: string) => ({
            name: 'stub-provider',
            generateEmbedding: async (text: string) => new Array(1536).fill(0),
            embed: async (text: string) => new Array(1536).fill(0),
        }),
        getDefaultProvider: () => ({
            name: 'stub-provider',
            generateEmbedding: async (text: string) => new Array(1536).fill(0),
            embed: async (text: string) => new Array(1536).fill(0),
        }),
    };
}
