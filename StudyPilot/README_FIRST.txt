STUDYPILOT - START HERE

This is a starter Android Studio project based on your Assignment 1 proposal and Assignment 2 requirements.

HOW TO OPEN
1. Extract the ZIP file to a normal folder (not inside Downloads if Windows permissions cause issues).
2. Open Android Studio.
3. Choose File > Open.
4. Select the extracted StudyPilot folder (the folder containing settings.gradle.kts).
5. If Android Studio asks to use JDK 17, select the embedded JDK / JBR 17.
6. Click Sync Now and wait for Gradle sync to finish.
7. If Android Studio asks to install Android SDK Platform 36 or Build Tools, accept the installation.
8. Select your Pixel 10a emulator and press the green Run triangle.

IF YOUR EXISTING PROJECT IS ALREADY OPEN
You can either open this complete project in a new Android Studio window (recommended), or copy the app/src/main folder and app/build.gradle.kts into your existing project. Opening this complete project is easier for a beginner.

WHAT IS IMPLEMENTED
- Compose Material 3 UI
- Home dashboard
- Study planner with add-task form and validation
- Task detail, mark complete and delete
- Practice quiz with instant feedback
- Progress dashboard based on Room tasks
- Offline-friendly sample materials screen
- Room Entity, DAO, Database, Repository
- ViewModel and Navigation Compose
- Data persists locally between app launches

IMPORTANT
- The materials screen contains sample resource entries, not downloadable PDF files.
- The practice questions are sample questions embedded in the app.
- AI tutor, cloud sync, real reminders and real downloadable study files are not implemented.
- Use your own screenshots and explain honestly what works in your demonstration.
- Assignment 2 asks for group task division, annotated screenshots, Room design, code explanations and individual reflection in the report.

If Gradle sync fails:
- Check internet connection.
- Android Studio > Tools > SDK Manager: install Android SDK Platform 36.
- File > Settings > Build, Execution, Deployment > Build Tools > Gradle: use JDK 17.
- Press File > Sync Project with Gradle Files.
- Read the first red error in the Build window; do not randomly change versions.
