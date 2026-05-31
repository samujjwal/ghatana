declare module "@ghatana/voice-ui" {
  import type { FC, InputHTMLAttributes } from "react";

  export interface VoiceInputProps extends InputHTMLAttributes<HTMLInputElement> {
    value?: string;
    onTranscript?: (transcript: string) => void;
    disabled?: boolean;
    listening?: boolean;
    language?: string;
  }

  export const VoiceInput: FC<VoiceInputProps>;
  export function useBrowserSpeechRecognition(): {
    listening: boolean;
    transcript: string;
    start: () => void;
    stop: () => void;
    error: Error | null;
    supported: boolean;
  };
  export default VoiceInput;
}
