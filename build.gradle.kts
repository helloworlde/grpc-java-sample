subprojects {
    apply(plugin = "java")
    apply(plugin = "application")


    group = "io.github.helloworlde"
    version = "0.0.1"

    repositories {
        mavenCentral()
        jcenter()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}