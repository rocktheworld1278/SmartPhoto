# SmartPhoto Android MVP (v0.1)

## What this starter implements
- Native Android / Kotlin / Jetpack Compose shell.
- Manual multi-select import using Android's document picker.
- Local reversible DEFLATE compression of the **original file bytes**.
- SHA-256 integrity verification before an imported archive is listed.
- Restore to a user-selected destination with a second SHA-256 check.
- Local storage dashboard and archive deletion.

## Important limitations
- This is a source project, not a prebuilt APK.
- Internal app-private storage is implemented. SD-card destination support is planned for the next milestone using Android's Storage Access Framework tree picker.
- Automatic new-photo monitoring and WorkManager queue are not implemented in this first scaffold.
- The app stores exact original bytes; it does not promise a fixed compression ratio. JPEG/HEIC/PNG files may save little or no space.
- Do not delete originals until you have independently tested restore and verified backups.
- The starter uses a simple archive header and DEFLATE stream; production hardening, streaming large-file tests, crash-safe temp-file commits, duplicate detection, and database indexing are still required.

## Build
Open this folder in Android Studio (Hedgehog or newer), allow Gradle sync, then run the `app` configuration on an Android 8.0+ device.

## Next milestones
1. Harden archive format and add large-file streaming / atomic commits.
2. Add SD-card archive destination with ACTION_OPEN_DOCUMENT_TREE.
3. Add MediaStore observer + WorkManager background import queue.
4. Add thumbnails/gallery, albums, duplicate detection, and settings.
5. Add migration assistant for Google Photos/local originals, with explicit verification and deletion confirmation.
