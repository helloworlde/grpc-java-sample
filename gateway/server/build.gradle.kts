import com.google.protobuf.gradle.*

plugins {
    java
    idea
    application
    id("com.google.protobuf") version "0.8.14"
    id("io.freefair.lombok") version "5.3.0"
}

repositories {
    mavenCentral()
    jcenter()
}

val grpcVersion = "1.34.1"
val protocVersion = "3.12.0"
val slf4jVersion = "1.7.25"

dependencies {
    implementation("io.grpc:grpc-netty:${grpcVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")
    implementation("org.slf4j:slf4j-simple:${slf4jVersion}")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation("junit:junit:4.13")
}

sourceSets {
    main {
        proto {
            srcDir("src/main/resources/proto")
        }
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${protocVersion}" }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
            }
        }
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("server.jar")
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    manifest {

        attributes["Main-Class"] = "io.github.helloworlde.grpc.HelloWorldServer"
    }
}