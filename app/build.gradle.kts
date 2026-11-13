plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.helltar.fosshine"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.helltar.fosshine"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    // a release is signed only when the environment names a keystore; without one it comes out unsigned,
    // so the project builds for anyone
    val keystore = System.getenv("ANDROID_KEYSTORE")
    if (keystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                // a PKCS12 keystore has one password for the store and the key
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                // nearly all of the dex is compose and material, little of it used. src/main/keepRules holds
                // the one rule: names stay, so a stack trace from a user reads without a mapping
                enable = true
            }
        }
    }

    packaging {
        dex {
            // stored dex is the default from minSdk 28 on; deflating it makes the apk smaller, at the cost of
            // the installer unpacking it on the device
            useLegacyPackaging = true
        }
    }

    dependenciesInfo {
        // the dependency list agp adds to the signing block is encrypted with a key of google's, so nobody
        // else can check what it holds, and izzyondroid flags an apk that carries it
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// a release is always compiled whole. compiled incrementally, the compose compiler takes the stability of a
// class it does not recompile from the previous build's output instead of inferring it from the source, and
// the bytecode then differs from a clean build of the same commit, which is what a rebuilder compares with.
// it is set after evaluation because the kotlin plugin switches the flag back on until then
afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        if (name.contains("Release")) {
            incremental = false
        }
    }
}
