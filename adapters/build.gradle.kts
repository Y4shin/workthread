dependencies {
    api(project(":contract"))

    // The store driver. Its version is pinned here rather than left to any
    // platform, because the Spring Boot BOM would move it back.
    implementation(libs.sqliteJdbc)
}
