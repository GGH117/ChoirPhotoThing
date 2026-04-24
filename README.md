# Choir Manager

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.2-green)

A cross-platform desktop application for managing choir operations — built with Java 21 + JavaFX.

## Features

| Module | Status |
|---|---|
| 👥 Roster management | ✅ Complete |
| ✅ Attendance tracking | ✅ Complete |
| 📅 Event scheduling | ✅ Complete |
| 📷 Photo management (Google Drive) | ✅ Complete |
| ✨ Auto-Sort Drive photos | ✅ Complete |
| 🤖 AI face detection & tagging | ✅ Complete |

## Requirements

- Java 21+
- Maven 3.8+

## Quick Start

```bash
git clone https://github.com/YOUR_USERNAME/ChoirManager.git
cd ChoirManager
mvn javafx:run
```

## Download a Release

Head to the [Releases page](https://github.com/GGH117/ChoirManager/releases) and
download the latest `choir-manager-X.Y.Z.jar`.

```bash
java -jar choir-manager-1.1.0.jar
```

> **macOS note:** JavaFX requires `--add-opens` flags when running the fat JAR.
> Use the bundled `run.sh` / `run.bat` scripts included in the release zip.

## Building from Source

```bash
mvn package
java -jar target/choir-manager-1.1.0.jar
```

## Project Structure

```
src/main/java/com/choirmanager/
├── App.java
├── db/
│   ├── DatabaseManager.java
│   ├── MemberDAO.java
│   ├── EventDAO.java
│   ├── AttendanceDAO.java
│   └── PhotoDAO.java
├── model/
│   ├── Member.java
│   ├── Event.java
│   ├── AttendanceRecord.java
│   ├── DetectedFace.java
│   └── Photo.java
├── service/
│   ├── GoogleDriveService.java
│   ├── FaceRecognitionService.java
│   └── DriveAutoSortService.java   ← new in 1.1.0
└── ui/
    ├── attendance/
    │   ├── AttendanceView.java
    │   └── EventDialog.java
    ├── photos/
    │   ├── PhotoView.java           ← updated in 1.1.0
    │   ├── PhotoDetailView.java
    │   ├── TagConfirmDialog.java
    │   └── AutoSortDialog.java      ← new in 1.1.0
    └── roster/
        ├── RosterView.java
        └── MemberDialog.java
```

## Google Drive Setup

1. Create a project at [Google Cloud Console](https://console.cloud.google.com)
2. Enable the **Drive API**
3. Create **OAuth 2.0 credentials** (Desktop app type)
4. Download `credentials.json` and place it in the same directory as the JAR

Tokens are cached in a `tokens/` folder after the first browser-based login —
subsequent launches are silent.

## Auto-Sort

Click **✨ Auto-Sort All** after connecting to Drive. The app will:

- Scan every folder in your Drive
- Infer event dates from folder names (`2024-03-15`, `March 2024`, etc.)
- Match photos to existing events within a ±3 day window
- Auto-create events for unrecognised folders
- Download thumbnails to a local `photo_cache/` folder

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
