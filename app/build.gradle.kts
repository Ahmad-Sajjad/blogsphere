plugins {
    alias(libs.plugins.android.application)
    // Processes google-services.json and wires Firebase config into the build.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.blogsphere"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // IMPORTANT: this must match the package_name in google-services.json
        // (com.company.blogsphere). The Kotlin source package / `namespace` above stays
        // com.example.blogsphere, so no source files need renaming.
        applicationId = "com.company.blogsphere"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.viewpager2)

    // ---- Firebase ----------------------------------------------------------
    // The BoM (Bill of Materials) keeps all Firebase library versions in sync.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)        // email/password + Google sign-in
    implementation(libs.firebase.firestore)   // cloud database (user profiles)
    implementation(libs.firebase.analytics)   // basic usage analytics (free)

    // ---- Google Sign-In + coroutine helpers --------------------------------
    implementation(libs.play.services.auth)              // GoogleSignInClient
    implementation(libs.kotlinx.coroutines.android)      // coroutine dispatchers
    implementation(libs.kotlinx.coroutines.play.services) // Task<T>.await() extension

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
