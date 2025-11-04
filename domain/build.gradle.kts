plugins {
    `module-parent`
}

dependencies {
    implementation(libs.pureconfig.core)
    implementation(libs.pureconfig.generic.scala3)

    implementation(libs.pekko.http)
    implementation(libs.pekko.http.backend)
    implementation(libs.pekko.stream)
    implementation(libs.pekko.actor)

}


