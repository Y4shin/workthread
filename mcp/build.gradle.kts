dependencies {
    implementation(project(":contract"))
    implementation(project(":wiring"))

    // The aggregator brings the SDK's payload closure; the JSON mapper is
    // supplied explicitly so the choice of Jackson 3 is this build's, not the
    // SDK's accident.
    implementation(libs.mcpSdk)
    implementation(libs.mcpJsonJackson3)
}
