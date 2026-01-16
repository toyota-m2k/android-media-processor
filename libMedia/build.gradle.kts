import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.toyota-m2k"
version="1.0"

configure<LibraryExtension> {
    namespace = "io.github.toyota32k.media.lib"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.coroutinesCore)
    implementation(libs.documentfile)
    api(libs.android.utilities)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junitExt)
    androidTestImplementation(libs.espressoCore)
}

// ./gradlew publishToMavenLocal
publishing {
    publications {
        // Creates a Maven publication called "release".
        register<MavenPublication>("release") {
            // You can then customize attributes of the publication as shown below.
            groupId = "com.github.toyota-m2k"
            artifactId = "android-media-processor"
            version = project.findProperty("githubReleaseTag") as String? ?: "LOCAL"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/toyota-m2k/android-media-processor")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}
