plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.micronaut.application") version "4.4.2"
    id("io.micronaut.test-resources") version "4.4.2" apply false
    id("io.micronaut.aot") version "4.4.2"
    id("eclipse")
    id("com.google.protobuf") version "0.9.4"
    jacoco
}

version = "0.1"
group = "com.globaldashboard.auth"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.security:micronaut-security-annotations")
    
    // gRPC
    implementation("io.micronaut.grpc:micronaut-grpc-server-runtime")
    implementation("io.micronaut.grpc:micronaut-grpc-client-runtime") // For testing or calling other gRPC services

    // Security & JWT
    implementation("io.micronaut.security:micronaut-security-jwt")
    
    // Redis
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.lettuce:lettuce-core")
    
    // Kafka
    implementation("io.micronaut.kafka:micronaut-kafka")
    
    // JSON
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.micronaut:micronaut-management")
    
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    // Test
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

application {
    mainClass.set("com.globaldashboard.auth.Application")
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

graalvmNative.toolchainDetection.set(false)

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.globaldashboard.auth.*")
    }
    aot {
        optimizeServiceLoading.set(false)
        convertYamlToJava.set(false)
        precomputeOperations.set(true)
        cacheEnvironment.set(true)
        optimizeClassLoading.set(true)
        deduceEnvironment.set(true)
        optimizeNetty.set(true)
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxDirectMemorySize=4g")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(false)
        csv.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
    
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it).matching {
            exclude("com/globaldashboard/auth/proto/**")
            exclude("com/globaldashboard/auth/grpc/**")
            exclude("**/*\$*")
        }
    }))
}
