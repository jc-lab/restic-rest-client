import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("signing")
    id("com.google.protobuf") version "0.9.4"
    kotlin("jvm") version Version.KOTLIN
}

group = "kr.jclab.restic"
version = Version.PROJECT

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    implementation("com.github.spotbugs:spotbugs-annotations:4.8.6")
    implementation("com.github.luben:zstd-jni:1.5.6-9")

    api("org.bouncycastle:bcprov-jdk18on:1.79")
    api("org.bouncycastle:bcpkix-jdk18on:1.79")
    api("commons-io:commons-io:2.18.0")
    api("org.apache.httpcomponents.client5:httpclient5:5.4.1")

    api("com.fasterxml.jackson.core:jackson-core:2.18.2")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set(project.name)
        description.set("jc-lab")
        url.set("https://jc-lab.tech")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("jclab")
                name.set("Joseph Lee")
                email.set("joseph@jc-lab.net")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/jc-lab/restic-rest-client.git")
            developerConnection.set("scm:git:ssh://git@github.com/jc-lab/restic-rest-client.git")
            url.set("https://github.com/jc-lab/restic-rest-client")
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signing.gnupg.keyName") || project.hasProperty("signing.keyId") }
}