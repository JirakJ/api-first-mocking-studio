plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
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
    testImplementation("io.mockk:mockk:1.13.8")
}

// Exclude IDE-provided libraries from plugin distribution
configurations {
    runtimeClasspath {
        exclude(group = "org.jetbrains.kotlin")
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
            name = "JetBrains Plugin Developer"
        }
        description = """
            Native API Client and Mocking Server for JetBrains IDEs.
            Fully replaces external API clients directly within your IDE.
            Supports OpenAPI 3.0/3.1, Swagger 2.0, and embedded Mock Server with dynamic data generation.
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
    jvmToolchain(17)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    test {
        useJUnitPlatform()
    }
}
