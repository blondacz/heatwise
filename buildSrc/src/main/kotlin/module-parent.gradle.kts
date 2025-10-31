import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

plugins {
    `java-library`
    `jvm-test-suite`
    scala
}

repositories {
    mavenCentral()
 }

scala {
    scalaVersion = "3.6.3"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.register("echo") {
    doLast {
        println("Hello, world!")
    }
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")


testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()
            dependencies {
                implementation(libs.findLibrary("scala-test").get())
                implementation(libs.findLibrary("junit-patform-launcher").get())
                implementation(libs.findLibrary("junit-patform-engine").get())
                implementation(libs.findLibrary("junit-scala-test-plus").get())
            }
        }
    }
}




