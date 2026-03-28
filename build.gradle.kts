plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "ru.it_spectrum.ai.redmine.mcp"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.ai.mcp.server)
    implementation(libs.spring.ai.mcp.server.webmvc)
    implementation("org.springframework:spring-web")
    implementation(libs.poi.ooxml)
    implementation(libs.pdfbox)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveBaseName.set("redmine-mcp-server")
    archiveVersion.set("")
    archiveClassifier.set("")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    group = "verification"
    description = "Runs integration tests that require a live Redmine connection"
}
