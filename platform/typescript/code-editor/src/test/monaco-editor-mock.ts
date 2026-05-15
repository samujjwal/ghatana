export const editor = {
  defineTheme: () => undefined,
  setTheme: () => undefined,
  createModel: () => ({
    dispose: () => undefined,
  }),
};

export const languages = {
  register: () => ({ dispose: () => undefined }),
  registerCompletionItemProvider: () => ({ dispose: () => undefined }),
};

export const MarkerSeverity = {
  Error: 8,
  Warning: 4,
  Info: 2,
  Hint: 1,
};

export default {
  editor,
  languages,
  MarkerSeverity,
};
