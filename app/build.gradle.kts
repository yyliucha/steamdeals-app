import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 站点根地址：release 固定生产；debug 可用 -PapiBaseUrl= 覆盖（本机联调走模拟器 10.0.2.2）
val defaultBaseUrl = "https://sd.yyliucha.cn"
val debugBaseUrl: String = (project.findProperty("apiBaseUrl") as String?) ?: defaultBaseUrl

// 签名：keystore 不入库（docs/APP-NATIVE.md §4.1）。本机 keystore.properties（gitignore）或
// -PstoreFile/-PstorePassword/-PkeyAlias/-PkeyPassword 提供；都没有则 release 不签名。
fun signingProps(): Properties? {
    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return null
    return Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "cn.yyliucha.steamdeals"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.yyliucha.steamdeals"
        minSdk = 24
        targetSdk = 36
        // versionCode = major*10000 + minor*100 + patch（覆盖旧 TWA 21800，必须单调递增）
        versionCode = 22500
        versionName = "2.25.0"

        buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
        buildConfigField("String", "FALLBACK_GH_RELEASE", "\"https://github.com/yyliucha/steamdeals/releases\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            // 本机联调允许明文 http（模拟器 10.0.2.2）；release 无此清单项
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfigs.create("release").apply {
                val props = signingProps()
                val store = (project.findProperty("storeFile") as String?) ?: props?.getProperty("storeFile")
                val storePass = (project.findProperty("storePassword") as String?) ?: props?.getProperty("storePassword")
                val alias = (project.findProperty("keyAlias") as String?) ?: props?.getProperty("keyAlias") ?: "steamdeals"
                val keyPass = (project.findProperty("keyPassword") as String?) ?: props?.getProperty("keyPassword") ?: storePass
                if (!store.isNullOrBlank() && !storePass.isNullOrBlank()) {
                    storeFile = file(store)
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg) {
        exclude(group = "com.caverock", module = "androidsvg-aar")
    }
    implementation(libs.androidsvg)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidsvg)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
