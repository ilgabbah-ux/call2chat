import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma: i dati stanno in keystore.properties (fuori dal repo)
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.gabbah.call2chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gabbah.call2chat"
        minSdk = 29
        targetSdk = 36
        versionCode = 13
        versionName = "1.9.2"
    }

    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "store"
    productFlavors {
        // APK da scaricare da GitHub: si aggiorna da solo
        create("github") { dimension = "store" }
        // Play Store: gli aggiornamenti li fa Google Play
        create("play") { dimension = "store" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true; viewBinding = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.16")
}

dependencies { testImplementation("junit:junit:4.13.2") }

android { testOptions { unitTests { isIncludeAndroidResources = true } } }
dependencies {
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
}
tasks.withType<Test>().configureEach { setForkEvery(1); systemProperty("robolectric.offline", "true"); systemProperty("robolectric.dependency.dir", "/root/robolectric-jars") }
