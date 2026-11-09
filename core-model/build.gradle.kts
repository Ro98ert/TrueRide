// Modul Kotlin/JVM pur — NU `com.android.library`.
// Consecinta: Android SDK-ul nu e pe classpath, deci `import android.*` nu
// compileaza. Invariantul din spec 4.2 e impus de build system, nu de disciplina.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Se compileaza cu JDK-ul disponibil (cel din Android Studio), dar se emite
// bytecode Java 17: Android nu accepta format de clasa mai nou, iar modulul
// asta ajunge in APK prin `app`.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Zero dependinte in afara de stdlib. Daca aici apare vreodata ceva,
// invariantul s-a rupt.
dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
