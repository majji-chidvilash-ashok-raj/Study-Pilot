# Room entities and DAOs
-keep class com.example.studypilot.data.StudyTask { *; }
-keep class com.example.studypilot.data.User { *; }
-keep class com.example.studypilot.data.StudyPilotDatabase { *; }

# Retrofit & Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.studypilot.api.** { *; }
