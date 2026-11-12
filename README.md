# JARVIS Android — native on-device starter

Target device: OnePlus Nord 4, 12 GB RAM / 256 GB storage.

This is a native Kotlin Android starter with a dark cyan JARVIS interface, local model import, local chat history, Android speech recognition and text-to-speech.

## Local AI
Import a compatible LiteRT-LM `.litertlm` model from the app. Model weights are not bundled in the APK. The app attempts GPU initialization first and falls back to CPU.

Model collection: https://huggingface.co/litert-community
Android guide: https://developers.google.com/edge/litert-lm/android

## Build
The GitHub Actions workflow builds a debug APK on pushes to `main` or via manual dispatch. After a successful run, download the `JARVIS-debug-APK` artifact and extract `app-debug.apk`.

## Limitations
This starter is local-only for AI chat. Online search, always-listening wake word and phone automation are not implemented. Speech recognition offline availability depends on the Android speech provider and installed language packs. This is a developer preview; first build may expose dependency/API compatibility issues.


Build trigger check: workflow should run on pushes to `main`.
