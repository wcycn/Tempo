plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val tempoApiBaseUrl = providers.gradleProperty("TEMPO_API_BASE_URL")
    .orElse("https://api.wcylab.cn/")
    .get()

android { namespace = "cn.wcylab.tempo"; compileSdk = 35
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "cn.wcylab.tempo"; minSdk = 26; targetSdk = 35; versionCode = 10; versionName = "0.4.5"
    }
    buildTypes {
        debug {
            // Android 内测直接访问公网服务器；正式环境应替换为 HTTPS 域名。
            buildConfigField("String", "API_BASE_URL", "\"$tempoApiBaseUrl\"")
        }
        release {
            // 公网部署：开放云安全组后使用；正式发布应替换为 HTTPS 域名。
            buildConfigField("String", "API_BASE_URL", "\"$tempoApiBaseUrl\"")
        }
    }
    // Java 与 Kotlin 必须使用同一个 JVM 编译目标，否则 Gradle 会在编译阶段终止。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    androidTestImplementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    // 固定网络栈版本，避免设备或其他依赖把 Retrofit 降级到旧版 OkHttp，
    // 导致部分荣耀设备在 HTTPS/网络切换时出现 Connection reset。
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("cn.6tail:lunar:1.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
