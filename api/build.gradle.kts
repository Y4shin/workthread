dependencies {
    implementation(project(":contract"))
    implementation(project(":wiring"))

    // Spring Boot arrives as a dependency platform only: no Spring Boot plugin
    // and no io.spring.dependency-management, whose Maven-style overriding
    // would fight the pins this build owns.
    implementation(platform(libs.springBootBom))
    implementation(libs.springBootStarterWebmvc)

    // The Spring Boot platform manages JUnit 6.1.3. JUnit 5.14.4 is this
    // build's pin, so the JUnit BOM is enforced on the test classpath here
    // instead of merely requested.
    testImplementation(enforcedPlatform(libs.junit.bom))
}
