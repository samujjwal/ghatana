# AI Voice Production Studio

Interactive voice replacement and music production studio that allows users to deconstruct songs, replace vocals with their AI voice, and create professional-quality music entirely digitally.

## Architecture

```
ai-voice/
├── apps/
│   └── desktop/              # Tauri + React desktop application
│       ├── src/              # React frontend
│       ├── src-tauri/        # Rust backend with Python bridge
│       └── package.json
└── libs/
    └── ai-voice-ui-react/    # Shared UI components and hooks
```

## Reuse-First Design

This project follows the **reuse-first** policy:

- **UI**: Built on `@ghatana/speech-ui-react` and `@ghatana/tts-ui-react`
- **Audio**: Uses `speech-audio-rust` for playback/export/resample
- **Transport**: Tauri commands prefixed `ai_voice_*`
- **Storage**: `${APP_DATA_DIR}/ghatana/speech/ai-voice/`

## Features

### Phase 1: Foundation (Implemented)

- ✅ Project scaffolding (Tauri + React)
- ✅ Audio I/O system via speech-audio-rust
- ✅ Rust/Python bridge for ML model calls
- ✅ Basic UI framework
- ✅ Model downloader for Demucs/RVC

### Phase 2: Interactive Features (Planned)

- Smart audio slicing
- Multi-track playback engine
- Real-time visualizations
- Phrase recording interface

### Phase 3: Pro Mixing & Mastering (Planned)

- Advanced voice processing
- Mixing engine with EQ/compression
- Mastering pipeline
- Export options

### Phase 4: Production Hardening (Planned)

- Performance optimization
- Privacy features
- Packaging and distribution

## Development

```bash
# Install dependencies
pnpm install

# Run development server
cd apps/desktop
pnpm tauri dev

# Build for production
pnpm tauri build
```

## ML Models Required

- **Demucs HT**: Stem separation (vocals, drums, bass, other)
- **RVC Base**: Voice conversion
- **CREPE**: Pitch detection

Models are downloaded automatically on first use or via Settings.

## Environment Variables

- `AI_VOICE_DATA_DIR`: Override data directory (default: `${APP_DATA_DIR}/ghatana/speech/ai-voice`)
- `AI_VOICE_MODELS_DIR`: Override models directory

## License

MIT
