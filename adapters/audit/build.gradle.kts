plugins {
    `module-parent`
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.bundles.circe)
    implementation(libs.pekko.kafka)

}


