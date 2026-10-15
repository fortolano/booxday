# BooxDay

A daily planner for Onyx BOOX e-ink tablets: a calendar, handwritten memos and handwritten task lists on one screen, made for the stylus and the e-ink display.

BooxDay is free and open source under the [GNU General Public License v3.0](LICENSE).

## Features

- Month calendar with the selected day's events and due tasks
- Handwritten Memos pages for each day, with text boxes, images and linked files
- Handwritten Tasks pages: ten rows a page, checkboxes, priorities, due dates, reminders, repeating tasks and subtasks
- Google Calendar and Outlook Calendar sync, or a calendar kept only on the tablet
- BOOX Notes integration: see your BOOX notes on the days they were written and link them to events
- Search across memos, tasks and notes
- Optional AI features (voice to event or task, handwriting to text, summaries, translation, task suggestions) using your own [OpenRouter](https://openrouter.ai) key
- Designed for e-ink: no animations, high contrast, large touch targets

## Requirements

- An Onyx BOOX tablet running Android 10 or later (tested on the BOOX Go 6 and Go 10.3)
- For AI features: your own OpenRouter API key, entered in Settings > AI Settings

## Install

Download the latest APK from the [Releases](../../releases) page and install it on the tablet.

## Build from source

Requirements: JDK 17 and the Android SDK (compile SDK 36).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release builds are signed with a key you provide, either through the environment variables `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, or through a `keystore.properties` file in the project root (it is ignored by git).

Calendar sync needs your own OAuth setup if you build and sign the app yourself: a Google Cloud OAuth client for Android (package `com.boox.day` and your signing certificate's SHA-1), and for Outlook an Azure app registration, whose client ID and redirect URI go in `app/src/main/res/raw/auth_config.json`.

## Support

BooxDay is shared as is: there is no technical or product support. Bug reports and pull requests are welcome on the [Issues](../../issues) page, but there is no promise of a reply or a fix. The project is still being improved, and new versions are published on the [Releases](../../releases) page.

## Privacy

Your memos, tasks and calendar are stored on the tablet. BooxDay has no server and sends nothing to its developer. It goes online to download Google's on-device handwriting model and for the features you turn on: calendar sync (Google or Microsoft), backup to your own Google Drive, and AI features (OpenRouter, with your own key).

## Credits

BooxDay began as a fork of [Notable](https://github.com/olup/notable) by olup and contributors, also licensed under GPL-3.0.

Third-party code shipped with the app:

- [boox-note-parser](https://github.com/hhornbacher/boox-note-parser) by hhornbacher (Apache-2.0), included as a git submodule in `native/boox-note-parser`. With the JNI wrapper in `native/boox-note-android-ffi` it is built into `app/src/main/jniLibs/arm64-v8a/libboox_note_android_ffi.so`, which reads BOOX Notes.
- The Onyx BOOX SDK (pen, device and base libraries, including `libneo_pen.so`) from Onyx International Inc., used for the e-ink pen and screen, as Notable does. It is not covered by this project's license.

BooxDay is an independent project. It is not affiliated with, endorsed by or sponsored by Onyx International Inc., Google LLC or Microsoft Corporation. "BOOX" and "Onyx" are trademarks of Onyx International Inc.

## License

Copyright © 2024–2026 Flavio Ortolano.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3. It is distributed WITHOUT ANY WARRANTY. See [LICENSE](LICENSE) for the full terms.
