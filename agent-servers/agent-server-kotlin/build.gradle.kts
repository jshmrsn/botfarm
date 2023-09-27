val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
   kotlin("jvm") version "1.9.10"
   id("io.ktor.plugin") version "2.3.4"
   id("com.github.johnrengelman.shadow") version "7.0.0"
   id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
   id("application")
}

sourceSets {
   main {
      kotlin {
         srcDir("../../shared/kotlin")
      }
   }
}


group = "botfarm"
version = "0.0.1"

application {
   mainClass.set("botfarm.agentserver.AgentServerApplicationKt")

   val isDevelopment: Boolean = project.ext.has("development")
   applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
   mavenCentral()
}



dependencies {
   implementation("com.knuddels:jtokkit:0.6.1")
   implementation("com.aallam.openai:openai-client:3.4.1")
   implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
   implementation("io.ktor:ktor-server-core-jvm")
   implementation("io.ktor:ktor-server-websockets-jvm")
   implementation("io.ktor:ktor-server-content-negotiation-jvm")
   implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
   implementation("io.ktor:ktor-server-call-logging-jvm")
   implementation("io.ktor:ktor-server-swagger-jvm")
   implementation("io.ktor:ktor-server-host-common-jvm")
   implementation("io.ktor:ktor-server-status-pages-jvm")
   implementation("io.ktor:ktor-server-resources")
   implementation("io.ktor:ktor-server-html-builder-jvm")
   implementation("io.ktor:ktor-server-netty-jvm")
   implementation("io.ktor:ktor-server-cio")
   implementation("io.ktor:ktor-client-java:$ktor_version")
   implementation("ch.qos.logback:logback-classic:$logback_version")
   testImplementation("io.ktor:ktor-server-tests-jvm")
   testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
