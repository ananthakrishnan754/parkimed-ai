// ParkiMed — top-level build file
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// google-services plugin is only needed once google-services.json exists;
// it is applied conditionally from app/build.gradle.kts.
buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.3")
    }
}