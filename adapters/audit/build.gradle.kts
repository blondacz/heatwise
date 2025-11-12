plugins {
    `module-scala`
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.bundles.circe)
    implementation(libs.pekko.kafka)

    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.pekko.stream.testkit)
}


