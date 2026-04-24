plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin") version "2.7.7"
    id("kotlin-parcelize")

}

android {
    namespace = "com.senderlink.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.senderlink.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // TODO: Configurar URL de producción real (HTTPS) antes de release
        buildConfigField("String", "BASE_URL", "\"http://192.168.1.20:3000/\"")

        // Google Maps API Key desde local.properties (no hardcodear en manifest)
        val mapsKey = project.findProperty("GOOGLE_MAPS_API_KEY") as? String ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "BASE_URL", "\"https://tu-dominio-produccion.com/\"")
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

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)

    // Firebase BOM (gestiona versiones)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    // Firebase
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")




    // Jetpack Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Google Maps
    implementation(libs.play.services.maps)
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp (para Google Directions API) ✅ NUEVO
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // OkHttp Logging Interceptor
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    // Gson (ya lo tienes con Retrofit, pero asegúrate)
    implementation("com.google.code.gson:gson:2.10.1")

    // Glide para imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    // CircleImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")
    //dataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")




    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
// Mockito (si usas Mockito)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}