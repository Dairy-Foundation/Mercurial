plugins {
    id("dev.frozenmilk.jvm-library") version "12.0.0-1.2.1"
    id("dev.frozenmilk.publish") version "0.1.0"
    id("dev.frozenmilk.doc") version "0.1.0"
    id("dev.frozenmilk.build-meta-data") version "0.1.0"
}

repositories {
    maven {
        name = "dairyReleases"
        url = uri("https://repo.dairy.foundation/releases")
    }
}

ftc {
    kotlin()
}

dependencies {
    api("dev.frozenmilk.dairy:Util:1.3.0")
    api("dev.frozenmilk:Sinister:2.3.0")
    compileOnlyApi("org.jetbrains:annotations:26.1.0")
    testImplementation("junit:junit:4.13.2")
}

meta {
    packagePath = "dev.frozenmilk.dairy"
    name = "Mercurial"
    registerField("name", "String", "\"dev.frozenmilk.dairy.Mercurial\"")
    registerField("clean", "Boolean") { "${dairyPublishing.clean}" }
    registerField("gitRef", "String") { "\"${dairyPublishing.gitRef}\"" }
    registerField("snapshot", "Boolean") { "${dairyPublishing.snapshot}" }
    registerField("version", "String") { "\"${dairyPublishing.version}\"" }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "dev.frozenmilk.dairy"
            artifactId = "Mercurial"

            artifact(dairyDoc.dokkaHtmlJar)
            artifact(dairyDoc.dokkaJavadocJar)

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}
