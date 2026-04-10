# Choir Manager

A cross-platform desktop application for managing choir operations — built with Java 21 + JavaFX.

## Features

| Module | Status |
|---|---|
| 👥 Roster management | ✅ Complete |
| ✅ Attendance tracking | 🚧 In progress |
| 📅 Scheduling | 🚧 In progress |
| 📷 Photo management (Google Drive) | 🚧 In progress |

## Requirements

- Java 21+
- Maven 3.8+

## Running the App

```bash
mvn javafx:run
```

## Building a Fat JAR

```bash
mvn package
java -jar target/choir-manager-1.0-SNAPSHOT.jar
```

## Project Structure

```
src/main/java/com/choirmanager/
├── App.java                   # Entry point
├── db/
│   ├── DatabaseManager.java   # SQLite connection & schema
│   ├── MemberDAO.java         # Member CRUD
│   ├── EventDAO.java          # Event CRUD (coming soon)
│   └── AttendanceDAO.java     # Attendance CRUD (coming soon)
├── model/
│   ├── Member.java
│   └── Event.java
└── ui/
    ├── roster/
    │   ├── RosterView.java    # Roster table + toolbar
    │   └── MemberDialog.java  # Add/edit member dialog
    ├── attendance/            # Coming soon
    ├── schedule/              # Coming soon
    └── photos/                # Coming soon
```

## Database

SQLite database (`choir_manager.db`) is created automatically on first run in the working directory.

## Google Drive Integration

Photo sync requires a `credentials.json` from the [Google Cloud Console](https://console.cloud.google.com/).  
Place it in the project root — it is excluded from version control via `.gitignore`.
