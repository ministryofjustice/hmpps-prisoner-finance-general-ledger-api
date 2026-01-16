plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.0.3"
  kotlin("plugin.spring") version "2.3.10"
  id("org.jetbrains.kotlin.plugin.noarg") version "2.3.10"
  id("jacoco")
  kotlin("plugin.jpa") version "2.3.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.0.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.postgresql:postgresql:42.7.10")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.0.0")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.37") {
    exclude(group = "io.swagger.core.v3")
  }

  testImplementation("org.testcontainers:postgresql:1.21.4")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    freeCompilerArgs.addAll("-Xannotation-default-target=param-property")
  }
}

tasks {

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }

  register<Test>("unitTest") {
    group = "verification"
    description = "Runs unit tests excluding integration tests"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["main"].output + configurations["testRuntimeClasspath"] + sourceSets["test"].output
    filter {
      excludeTestsMatching("uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration*")
    }
    extensions.configure(JacocoTaskExtension::class) {
      destinationFile = layout.buildDirectory.file("jacoco/unitTest.exec").get().asFile
    }
  }

  register<Test>("integrationTest") {
    description = "Runs the integration tests"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["main"].output + configurations["testRuntimeClasspath"] + sourceSets["test"].output
    filter {
      includeTestsMatching("uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration*")
    }
    extensions.configure(JacocoTaskExtension::class) {
      destinationFile = layout.buildDirectory.file("jacoco/integrationTest.exec").get().asFile
    }
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }

  testlogger {
    theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA
  }
}

tasks.register<JacocoReport>("jacocoUnitTestReport") {
  dependsOn("unitTest")
  executionData.setFrom(layout.buildDirectory.file("jacoco/unitTest.exec"))
  classDirectories.setFrom(sourceSets.main.get().output)
  sourceDirectories.setFrom(sourceSets.main.get().allSource)

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/unit"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/unit/jacoco.xml"))
  }

  doLast {
    val reportFile = reports.xml.outputLocation.get().asFile
    if (reportFile.exists()) {
      val content = reportFile.readText()
      val updatedContent = content.replaceFirst("name=\"${project.name}\"", "name=\"Unit Tests\"")
      reportFile.writeText(updatedContent)
    }
  }
}

tasks.register<JacocoReport>("jacocoTestIntegrationReport") {
  dependsOn("integrationTest")
  executionData.setFrom(layout.buildDirectory.file("jacoco/integrationTest.exec"))

  classDirectories.setFrom(sourceSets.main.get().output)
  sourceDirectories.setFrom(sourceSets.main.get().allSource)

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/integration"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/integration/jacoco.xml"))
  }

  doLast {
    val reportFile = reports.xml.outputLocation.get().asFile
    if (reportFile.exists()) {
      val content = reportFile.readText()
      val updatedContent = content.replaceFirst("name=\"${project.name}\"", "name=\"Integration Tests\"")
      reportFile.writeText(updatedContent)
    }
  }
}

tasks.register<JacocoReport>("combineJacocoReports") {
  dependsOn("jacocoUnitTestReport", "jacocoTestIntegrationReport")

  executionData(
    layout.buildDirectory.file("jacoco/unitTest.exec"),
    layout.buildDirectory.file("jacoco/integrationTest.exec"),
  )

  classDirectories.setFrom(sourceSets.main.get().output)
  sourceDirectories.setFrom(sourceSets.main.get().allSource)

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/combined"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/combined/jacoco.xml"))
  }

  doLast {
    val reportFile = reports.xml.outputLocation.get().asFile
    if (reportFile.exists()) {
      val content = reportFile.readText()
      val updatedContent = content.replaceFirst("name=\"${project.name}\"", "name=\"Combined Tests\"")
      reportFile.writeText(updatedContent)
    }
  }
}

tasks.named("check") {
  dependsOn("unitTest", "integrationTest", "combineJacocoReports")
}
