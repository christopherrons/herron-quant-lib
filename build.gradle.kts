plugins {
	java
	id("maven-publish")
	id("io.spring.dependency-management") version "1.1.0"
}

// Project Configs
allprojects {
	repositories {
		mavenLocal()
		maven {
			name = "bytesafe"
			url = uri("https://herron.bytesafe.dev/maven/herron/")
			credentials {
				username = extra["username"] as String?
				password = extra["password"] as String?
			}
		}
	}

	apply(plugin = "maven-publish")
	apply(plugin = "java-library")

	group = "com.herron.exchange"
	version = "1.0.0-SNAPSHOT"
	if (project.hasProperty("releaseVersion")) {
		val releaseVersion: String by project
		version = releaseVersion
	}

	java {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	publishing {
		publications {
			create<MavenPublication>("herron.exchange") {
				artifactId = project.name
				// artifact("build/libs/${artifactId}-${version}.jar")
				from(components["java"])
			}
			repositories {
				maven {
					name = "bytesafe"
					url = uri("https://herron.bytesafe.dev/maven/herron/")
					credentials {
						username = extra["username"] as String?
						password = extra["password"] as String?
					}
				}
			}
		}
	}
}


dependencies {
	//Project Modules
	implementation(project(":quantlib"))

	// Internal Libs
	implementation(libs.common.api)
	implementation(libs.common)

	// External Libs
	implementation(libs.spring.boot.starter.web)
	implementation(libs.spring.boot.starter.parent)

	// External Test Libs
	testImplementation(testlibs.junit.jupiter.api)
	testImplementation(testlibs.junit.jupiter.engine)
}

// Tasks
val releaseDirName = "releases"
tasks.register<Tar>("buildAndPackage") {
	dependsOn("clean")
	dependsOn("build")
	tasks.findByName("build")?.mustRunAfter("clean")
	compression = Compression.GZIP
	archiveExtension.set("tar.gz")
	destinationDirectory.set(layout.buildDirectory.dir(releaseDirName))
	from(layout.buildDirectory.file("libs/${rootProject.name}-${version}.jar"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}