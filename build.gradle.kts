plugins {
    id("java")
    kotlin("jvm") version "1.9.20"
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
        intellijIdeaCommunity("2023.2")
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
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    
    test {
        useJUnitPlatform()
    }
}
