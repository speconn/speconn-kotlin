plugins {
    kotlin("multiplatform") version "2.3.21"
}

group = "speconn-runtime-kotlin"
version = "0.1.0"

repositories {
    mavenCentral()
    ivy {
        name = "specodec-github-releases"
        url = uri("https://github.com/specodec")
        patternLayout {
            artifact("[module]/releases/download/v[revision]/[module]-jvm-[revision].jar")
        }
        metadataSources { artifact() }
    }
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:3.1.3")
            implementation("io.specodec:specodec-kotlin:0.0.1")
        }
    }
}