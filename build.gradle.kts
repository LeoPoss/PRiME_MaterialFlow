plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "de.ur"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.webflux)
    developmentOnly(libs.spring.boot.devtools)

    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.snakeyaml)

    implementation(libs.camunda.engine.rest.jakarta)
    implementation(libs.camunda.bpm.spring.boot.starter.webapp)
    implementation(libs.camunda.bpm.spring.boot.starter.rest)
    implementation(libs.camunda.bpm.spring.boot.starter)

    runtimeOnly(libs.h2)

    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
