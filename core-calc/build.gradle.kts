// Motorul de calcul. Modul Kotlin/JVM pur, ca si core-model — testele rulează
// pe laptop, in milisecunde, fara emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pitest)
    alias(libs.plugins.kover)
}

// Vezi nota din core-model: compilam cu JDK-ul disponibil, emitem Java 17.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core-model"))
    testImplementation(libs.kotlin.test)
}

tasks.test {
    // Proprietatile de noapte (cazuri multe, seed din linia de comanda) ruleaza doar prin `noapte`.
    useJUnitPlatform { excludeTags("noapte") }
}

// D-348.4: proprietati cu seed pe invariantii din CLAUDE.md §5. Acelasi tipar ca `explorare` din core-parse.
//   ./gradlew :core-calc:noapte -Ptrueride.noapte.seed=<n> -Ptrueride.noapte.cazuri=<n>
tasks.register<Test>("noapte") {
    group = "verification"
    description = "Proprietati peste intrari generate cu -Ptrueride.noapte.seed si -Ptrueride.noapte.cazuri"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("noapte") }
    // Rezultatul depinde de proprietati: nu se reciclheaza din cache.
    outputs.upToDateWhen { false }
    for (nume in listOf("seed", "cazuri")) {
        providers.gradleProperty("trueride.noapte.$nume").orNull?.let { systemProperty("trueride.noapte.$nume", it) }
    }
}

// Criteriul M2 din spec 10 cere acoperire >= 95% pe acest modul.
// `koverVerify` face din prag o conditie de build, nu o cifra pe care o citesti
// intr-un raport si o ignori.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}

// D-347.3: mutatii PIT, doar pentru noapte (nu intra in `check`). Mutantii trebuie omorati de suita de ZI: etichetele
// `noapte` si `explorare` sunt excluse (pitest-junit5-plugin le citeste din `excludedGroups` ca TagFilter.excludeTags).
// `threads` = nuclee - 1, ca PC-ul sa ramana folosibil.
pitest {
    junit5PluginVersion.set(libs.versions.pitestJunit5)
    pitestVersion.set(libs.versions.pitestEngine)
    targetClasses.set(setOf("app.trueride.core.calc.*"))
    excludedGroups.set(setOf("noapte", "explorare"))
    threads.set((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))
    outputFormats.set(setOf("XML"))
    timestampedReports.set(false)
}
