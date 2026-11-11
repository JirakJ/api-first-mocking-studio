plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

group = "com.jakumjirak"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugins("com.intellij.java", "org.jetbrains.plugins.terminal")
        pluginVerifier()
        zipSigner()
    }
    
    // OpenAPI Parser
    implementation("io.swagger.parser.v3:swagger-parser:2.1.18")
    
    // Mock Server (Ktor)
    implementation("io.ktor:ktor-server-core-jvm:2.3.5")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.5")
    
    // JSON Path for scripting
    implementation("com.jayway.jsonpath:json-path:2.9.0")
    
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The IntelliJ test runtime references JUnit 4 classes even for pure JUnit 5 tests.
    testRuntimeOnly("junit:junit:4.13.2")
}

// Exclude IDE-provided libraries from plugin distribution
configurations {
    runtimeClasspath {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.apifirst.mocking.studio"
        name = "API First & Mocking Studio"
        version = providers.gradleProperty("pluginVersion")
        
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
        
        vendor {
            name = "Jakub Jirák"
            url = "https://github.com/JirakJ/api-first-mocking-studio"
        }
        description = """
            <p>API client and mock server inside your JetBrains IDE.</p>
            <ul>
              <li>API Explorer tool window: requests with Raw, form, multipart and GraphQL bodies, auth, environments, history and collections</li>
              <li>Pre-request scripts, response variable extraction and assertions</li>
              <li>Import OpenAPI 3.0/3.1, Swagger 2.0, Postman collections and cURL commands; export cURL, Python and JavaScript snippets</li>
              <li>Tools | API First: local mock server generated from an OpenAPI spec, and .http file generation</li>
            </ul>
        """.trimIndent()
    }
    
    publishing {
        token = System.getenv("PLUGIN_TOKEN")
    }

    pluginVerification {
        ides {
            create("IC", "2025.1.7") { useInstaller = false }
            create("IC", "2025.2.6.1") { useInstaller = false }
            create("IU", "2025.3.4") { useInstaller = true }
            create("IU", "261.23567.71") { useInstaller = true }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    test {
        useJUnitPlatform()
    }
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}
