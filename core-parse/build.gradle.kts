// Modul Kotlin/JVM pur: tokeni OCR -> ParsedOffer (spec §4, M4). Fara Android pe
// classpath (invariantul de arhitectura impus de build), fara dependente in afara
// de core-model.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pitest)
    // CititorFixturi e folosit si de core-lant (lantul pe fixture-uri, M5+M6):
    // test fixtures il publica fara sa-l mute in codul de productie.
    `java-test-fixtures`
}

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
    // Etichetele ruleaza doar prin task-ul lor (`explorare`, `noapte`): in suita de zi ar sta degeaba sau ar dura prea mult.
    useJUnitPlatform { excludeTags("explorare", "noapte") }
    // Fixture-urile sunt intrare a testului: o fixtura noua re-ruleaza M4 fara --rerun.
    val fixturi = rootProject.file("fixtures")
    inputs.dir(fixturi)
    systemProperty("fixtures.dir", fixturi.absolutePath)
}

// D-288.6: replay cu parserul real pe un director de fixture-uri (il porneste un script privat).
//   ./gradlew -q :core-parse:replay -Preplay.dir=<fixture-uri> -Preplay.out=<rezultat.tsv> [-Preplay.valori=<amprente.tsv>]
// Caile relative se rezolva fata de radacina proiectului (JavaExec ruleaza din core-parse/, nu de acolo unde stai).
// Clasa vine din sursele de test (ReplayFixturi.kt): niciun cod nou in productie, nicio dependenta noua.
tasks.register<JavaExec>("replay") {
    group = "verification"
    description = "Ruleaza parserul real pe fixture-urile din -Preplay.dir si scrie TSV in -Preplay.out"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.trueride.core.parse.ReplayFixturiKt")
    val dir = providers.gradleProperty("replay.dir")
    val out = providers.gradleProperty("replay.out")
    val valori = providers.gradleProperty("replay.valori")
    argumentProviders.add(
        CommandLineArgumentProvider {
            fun cale(p: String) = rootProject.file(p).absolutePath
            listOfNotNull(
                cale(dir.orNull ?: throw GradleException("lipseste -Preplay.dir")),
                cale(out.orNull ?: throw GradleException("lipseste -Preplay.out")),
                valori.orNull?.let(::cale),
            )
        },
    )
}

// D-347.2: explorarea de noapte. Aceleasi perturbari ca suita sintetica, dar cu seed si numar de variante din linia de
// comanda; scrie un TSV cu variantele la care parserul da o valoare GRESITA. Nu pica la gasirea lor: le raporteaza.
//   ./gradlew :core-parse:explorare -Ptrueride.explorare.seed=<n> -Ptrueride.explorare.variante=<n> -Ptrueride.explorare.iesire=<cale>
tasks.register<Test>("explorare") {
    group = "verification"
    description = "Genereaza variante sintetice cu alt seed si scrie in -Ptrueride.explorare.iesire cele citite gresit"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("explorare") }
    // Rezultatul depinde de proprietati si scrie un fisier din afara build/: nu se reciclheaza din cache.
    outputs.upToDateWhen { false }
    for (nume in listOf("seed", "variante", "iesire")) {
        providers.gradleProperty("trueride.explorare.$nume").orNull?.let {
            // Calea relativa se rezolva fata de radacina proiectului (testul ruleaza din core-parse/).
            systemProperty("trueride.explorare.$nume", if (nume == "iesire") rootProject.file(it).absolutePath else it)
        }
    }
}

// D-348.5: text aleator in parser. Aceleasi proprietati de linie de comanda ca `:core-calc:noapte`.
//   ./gradlew :core-parse:noapte -Ptrueride.noapte.seed=<n> -Ptrueride.noapte.cazuri=<n>
tasks.register<Test>("noapte") {
    group = "verification"
    description = "Text aleator in parser cu -Ptrueride.noapte.seed si -Ptrueride.noapte.cazuri"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("noapte") }
    // Rezultatul depinde de proprietati: nu se reciclheaza din cache.
    outputs.upToDateWhen { false }
    for (nume in listOf("seed", "cazuri")) {
        providers.gradleProperty("trueride.noapte.$nume").orNull?.let { systemProperty("trueride.noapte.$nume", it) }
    }
    // Testele de noapte care judeca fixture-uri (cele din repo, ca exemplu cunoscut) citesc aceeasi proprietate ca suita de zi.
    systemProperty("fixtures.dir", rootProject.file("fixtures").absolutePath)
    // D-350.5: fixture-urile istorice de pe disc (checkout-ul principal, doar citire). Cale absoluta data de faza; fara ea
    // testul pe date reale se sare. Numaratorile pleaca intr-un TSV sub build/, citit de faza `noapte_jvm`.
    providers.gradleProperty("trueride.noapte.fixturi").orNull?.let {
        systemProperty("trueride.noapte.fixturi", it)
        systemProperty("trueride.noapte.fixturi.iesire", layout.buildDirectory.file("noapte-fixturi.tsv").get().asFile.absolutePath)
    }
    // Cu clasa de text aleator scoasa nu mai ramane niciun test cu tag `noapte`: Gradle 9 ar pica task-ul fara asta.
    failOnNoDiscoveredTests = false
    filter.isFailOnNoMatchingTests = false
    // Faza `noapte_jvm` scoate clasa de text aleator (o ruleaza doar `text_aleator`, cu alt seed si alta comanda).
    providers.gradleProperty("trueride.noapte.exclude").orNull?.let { filter.excludeTestsMatching(it) }
}

// D-347.3: mutatii PIT, doar pentru noapte (nu intra in `check`). Mutantii trebuie omorati de suita de ZI: etichetele
// `noapte` si `explorare` sunt excluse (pitest-junit5-plugin le citeste din `excludedGroups` ca TagFilter.excludeTags).
// `threads` = nuclee - 1, ca PC-ul sa ramana folosibil.
pitest {
    junit5PluginVersion.set(libs.versions.pitestJunit5)
    pitestVersion.set(libs.versions.pitestEngine)
    targetClasses.set(setOf("app.trueride.core.parse.*"))
    excludedGroups.set(setOf("noapte", "explorare"))
    // Aceeasi proprietate ca in tasks.test: testele citesc fixture-urile din radacina, fara ea 35 pica fara mutatie.
    jvmArgs.add("-Dfixtures.dir=${rootProject.file("fixtures").absolutePath}")
    threads.set((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))
    outputFormats.set(setOf("XML"))
    timestampedReports.set(false)
}
