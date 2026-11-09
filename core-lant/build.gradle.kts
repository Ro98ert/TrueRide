// Modul Kotlin/JVM pur (D-036): automatul cardului — ordinea surselor (D-033),
// starile E6 (D-035) si regula "nicio cifra dintr-o oferta anterioara"
// (invariantul 5). Fara Android pe classpath: compilatorul, nu disciplina,
// tine logica riscanta testabila pe laptop.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
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
    api(project(":core-parse"))
    api(project(":core-calc"))
    testImplementation(libs.kotlin.test)
    testImplementation(testFixtures(project(":core-parse")))
}

tasks.test {
    useJUnitPlatform()
    // Aceleasi fixture-uri ca M4; o fixtura noua re-ruleaza lantul fara --rerun.
    val fixturi = rootProject.file("fixtures")
    inputs.dir(fixturi)
    systemProperty("fixtures.dir", fixturi.absolutePath)
}

kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
