plugins {
    `module-parent`
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.pekko.http)
    implementation(libs.pekko.actor)
    implementation(libs.pekko.stream)
    implementation(libs.sttp.core)
    implementation(libs.sttp.circe)
    implementation(libs.sttp.pekko.backend )
}

