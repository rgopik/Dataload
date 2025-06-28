# Dataload: Anatomy Questions Uploader

This is a standalone Kotlin/JVM console app to upload hardcoded Anatomy questions to Firestore using the Firebase Admin SDK.

## Setup
1. Place your Firebase service account JSON file in the project root (e.g., `serviceAccount.json`).
2. Edit `src/main/kotlin/com/agentic/dataload/Main.kt` to update the questions if needed.

## Usage
- Build the project:
  ```
  ./gradlew build
  ```
- Run the uploader:
  ```
  ./gradlew run --args='serviceAccount.json'
  ```

This will upload all hardcoded Anatomy questions to the `anatomy_questions` collection in your Firestore project.
