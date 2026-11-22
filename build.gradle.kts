// The root build holds the shared module convention. Every module is a Java 21
// library; a module's own build file declares only its dependency edges, so the
// architecture's edge table is readable in one place per module.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    base
}

// The test libraries every module runs, taken from the one version catalogue.
// Read here rather than inside the subprojects block, where the catalogue
// accessor resolves against the subproject.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val junitBom = catalog.findLibrary("junit-bom").get()
val junitJupiter = catalog.findLibrary("junit-jupiter").get()
val junitPlatformLauncher = catalog.findLibrary("junit-platform-launcher").get()
val assertjCore = catalog.findLibrary("assertj-core").get()

subprojects {
    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    // JUnit 5 and AssertJ in every module, so no module can forget its tests.
    dependencies {
        add("testImplementation", platform(junitBom))
        add("testImplementation", junitJupiter)
        add("testRuntimeOnly", junitPlatformLauncher)
        add("testImplementation", assertjCore)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
