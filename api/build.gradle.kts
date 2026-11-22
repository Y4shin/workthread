dependencies {
    implementation(project(":contract"))
    implementation(project(":wiring"))

    // Spring Boot arrives as a dependency platform only: no Spring Boot plugin
    // and no io.spring.dependency-management, whose Maven-style overriding
    // would fight the pins this build owns.
    implementation(platform(libs.springBootBom))
    implementation(libs.springBootStarterWebmvc)
}
