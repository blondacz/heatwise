plugins {
    `module-parent`
    `module-app`
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":adapters:octopus"))
    implementation(project(":adapters:audit"))
    implementation(project(":adapters:relay"))
    implementation(libs.pekko.http)
    implementation(libs.pekko.http.backend)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.actor)
    implementation(libs.logback)
    implementation(libs.sttp.core)

    testImplementation(libs.pekko.testkit)
}

