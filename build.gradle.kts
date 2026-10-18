plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.jpa") version "2.4.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jetbrains.kotlin.plugin.spring") version "2.4.0"
}

group = "si.ogrodje.metaos"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.1")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // implementation("org.springframework.boot:spring-boot-starter-web")
    // implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // MCP
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    // implementation("org.springframework.boot:spring-boot-starter-mustache")

    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // runtimeOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Native macOS DNS resolver for Netty (avoids the MacOSDnsServerAddressStreamProvider fallback warning).
    // runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.2.15.Final:osx-aarch_64")

    // PG
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}

dependencyManagement {
    imports { mavenBom("org.springframework.ai:spring-ai-bom:2.0.1") }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        javaParameters = true
        javaParameters = true   // tool JSON schemas need real parameter names
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
    systemProperties = System.getProperties().toMap() as Map<String, Any>
}
