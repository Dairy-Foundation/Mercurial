plugins {
	id("dev.frozenmilk.jvm-library") version "10.3.0-0.1.4"
	id("dev.frozenmilk.publish") version "0.0.5"
	id("dev.frozenmilk.doc") version "0.0.5"
	id("dev.frozenmilk.build-meta-data") version "0.0.2"
}

repositories {
	maven {
		name = "dairyReleases"
		url = uri("https://repo.dairy.foundation/releases")
	}
}

dependencies {
	api("dev.frozenmilk.dairy:Util:1.2.0")
	api("dev.frozenmilk:Sinister:2.2.0")
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
