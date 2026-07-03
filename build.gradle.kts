import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

repositories {
    maven {url = uri("c:\\Repository\\#idea_plugins\\local-repo")}
//    maven {url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")}
    mavenLocal()
    mavenCentral()
    intellijPlatform {
//        defaultRepositories()
        localPlatformArtifacts()
    }
}

dependencies {
    // Source: https://mvnrepository.com/artifact/com.jetbrains.intellij.java/java-compiler-ant-tasks
    implementation("com.jetbrains.intellij.java:java-compiler-ant-tasks:252.28539.13")
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        local("C:\\Program Files\\JetBrains\\IntelliJ IDEA 2025.2.6")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}
