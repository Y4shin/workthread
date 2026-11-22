dependencies {
    api(project(":contract"))

    // The store driver, pinned so no platform can move it: Spring Boot's BOM
    // manages the older 3.53.4.0.
    implementation(libs.sqliteJdbc)
}
