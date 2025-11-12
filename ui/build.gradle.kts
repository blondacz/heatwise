plugins {
    base
    id("com.github.node-gradle.node") version "7.0.2"
}


node {
    download.set(true)
    version.set("20.18.0")
    npmVersion.set("10.8.2")
    // Vite expects workingDir at project root
    workDir.set(layout.projectDirectory.dir(".gradle/node"))
    npmWorkDir.set(layout.projectDirectory.dir(".gradle/npm"))
}

val npmInstall by tasks.getting
val npmBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn(npmInstall)
    args.set(listOf("run", "build"))
    inputs.dir("src")
    inputs.file("index.html")
    inputs.file("package.json")
    inputs.file("tsconfig.json")
    inputs.file("vite.config.ts")
    outputs.dir("dist")
}

tasks.assemble {
    dependsOn(npmBuild)
}

tasks.register<Copy>("copyDist") {
    dependsOn(npmBuild)
    from("dist")
    into(layout.buildDirectory.dir("dist"))
}