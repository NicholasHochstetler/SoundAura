plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
    id("com.mikepenz.aboutlibraries.plugin")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cliffracertech.soundaura"
    compileSdk = 34
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    defaultConfig {
        applicationId = "com.cliffracertech.soundaura"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_19
        targetCompatibility = JavaVersion.VERSION_19
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "19"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    packaging {
        resources {
            excludes += "/META-INF/*"
        }
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

class RoomSchemaArgProvider(
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File
): CommandLineArgumentProvider {
    override fun asArguments() = listOf("room.schemaLocation=${schemaDir.path}")
}
ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        optIn.addAll(
            "kotlin.RequiresOptIn",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material:material:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.animation:animation:1.5.4")
    implementation("androidx.compose.animation:animation-graphics:1.5.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.28.0")
    implementation("com.google.accompanist:accompanist-insets:0.28.0")
    implementation("com.google.accompanist:accompanist-insets-ui:0.28.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("com.google.dagger:hilt-android:2.55")
    implementation("com.mikepenz:aboutlibraries-core:10.9.2")
    implementation("com.mikepenz:aboutlibraries-compose:10.9.2")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.6")
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    ksp("androidx.room:room-compiler:2.6.1")
    kapt("com.google.dagger:hilt-compiler:2.55")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:truth:1.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
    androidTestImplementation("androidx.test.ext:truth:1.6.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48")
}