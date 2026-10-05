import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing material lives outside version control. See keystore.properties.example.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.yuan3271.cloudrift"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.yuan3271.cloudrift"
        minSdk = 26
        targetSdk = 36
        versionCode = 52
        versionName = "0.3.5"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val path = keystoreProperties.getProperty("storeFile") ?: return@create
            storeFile = rootProject.file(path)
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // 侧载包，体积就是用户要下载的东西：不压缩时 dex 里躺着整套未用到的 androidx /
            // Compose，解压后 26 MB，压完 12.4 MB；R8 之后只剩用到的那部分（见 PLAN 0.2.38）。
            // 应用本体没有任何反射（不查 `getIdentifier`、不用 `Class.forName`），入口点写在
            // 清单里由 AGP 保号，依赖库的 consumer rules（Compose / coroutines / OkHttp）也会自动
            // 合进来，所以这里开全量压缩是安全的；保留规则与理由见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Material 3 Expressive lives on the 1.5 line; the BOM still pins 1.4.x.
    implementation(libs.androidx.compose.material3)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}
