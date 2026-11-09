// The root build does not build anything itself. It declares the plugins with
// `apply false` so their versions are pinned once for all modules; each module
// applies the ones it needs.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.pitest) apply false
}

// "Flaky test" night phase: with `-Ptrueride.ordine.seed=<n>` JUnit 5 shuffles classes and methods with a
// reproducible seed, so a test that depends on execution order fails and can be reproduced. Without the
// property the default order is kept.
subprojects {
    tasks.withType<Test>().configureEach {
        val seedOrdine = providers.gradleProperty("trueride.ordine.seed").orNull
        if (seedOrdine != null) {
            systemProperty("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer\$Random")
            systemProperty("junit.jupiter.testmethod.order.default", "org.junit.jupiter.api.MethodOrderer\$Random")
            systemProperty("junit.jupiter.execution.order.random.seed", seedOrdine)
        }
    }
}
