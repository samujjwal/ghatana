declare module '@ghatana/nlp-ui' {
  import type { FC, InputHTMLAttributes } from 'react';

  export interface NLQInputProps extends InputHTMLAttributes<HTMLInputElement> {
    value?: string;
    onNLQSubmit?: (query: string) => void;
    placeholder?: string;
    disabled?: boolean;
    loading?: boolean;
  }

  export const NLQInput: FC<NLQInputProps>;
  export function useNLQParse(): (query: string) => Promise<{ sql: string; confidence: number }>;
  export default NLQInput;
}
