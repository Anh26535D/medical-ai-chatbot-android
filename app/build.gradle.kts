import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.compose.compiler)
}

// Load local.properties for local development
val localProperties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

/**
 * Gets a secret from environment variables first, then local.properties.
 * Returns a quoted and escaped string for use in buildConfigField.
 */
fun getSecret(key: String, defaultValue: String = ""): String {
    val value = System.getenv(key) ?: localProperties.getProperty(key) ?: defaultValue
    return "\"${value.replace("\"", "\\\"").replace("\n", "\\n")}\""
}

/**
 * Loads prompts from prompts.txt file.
 */
fun loadPrompts(): Map<String, String> {
    val promptsFile = project.file("prompts.txt")
    if (!promptsFile.exists()) return emptyMap()

    val map = mutableMapOf<String, String>()
    var currentKey = ""
    val currentContent = StringBuilder()

    promptsFile.readLines().forEach { line ->
        if (line.startsWith("[") && line.endsWith("]")) {
            if (currentKey.isNotEmpty()) {
                map[currentKey] = currentContent.toString().trim()
            }
            currentKey = line.substring(1, line.length - 1)
            currentContent.setLength(0)
        } else {
            currentContent.append(line).append("\n")
        }
    }
    if (currentKey.isNotEmpty()) {
        map[currentKey] = currentContent.toString().trim()
    }
    return map
}

val promptsMap = loadPrompts()
fun getPrompt(key: String, default: String = ""): String {
    val value = promptsMap[key] ?: default
    return "\"${value.replace("\"", "\\\"").replace("\n", "\\n")}\""
}

val appVersionCode = 4
val appVersionName = "0.1.1-alpha.1"

android {
    namespace = "edu.hust.medicalaichatbot"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.hust.medicalaichatbot"
        minSdk = 27
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                // Fallback for local development if environment variables are not set
                storeFile = file("release.keystore")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "CHAT_SYSTEM_PROMPT", getPrompt("TRIAGE_MEDICAL_SYSTEM_PROMPT"))
            buildConfigField("String", "SUMMARY_SYSTEM_PROMPT", getPrompt("ANALYSIS_MEDICAL_SYSTEM_PROMPT"))
            buildConfigField("String", "SYMPTOM_CACHE_PROMPT", getPrompt("SYMPTOM_CACHE_PROMPT"))
            buildConfigField("String", "CONTEXT_LOCATION", getPrompt("CONTEXT_LOCATION", "Dưới đây là danh sách các cơ sở y tế/nhà thuốc gần vị trí của tôi nhất: %s"))
            buildConfigField("String", "CONTEXT_SYMPTOMS", getPrompt("CONTEXT_SYMPTOMS", "Các thông tin triệu chứng đã thu thập được: %s. KHÔNG hỏi lại những thông tin này nếu đã rõ ràng."))
            buildConfigField("String", "CONTEXT_SUMMARY", getPrompt("CONTEXT_SUMMARY", "Tóm tắt bệnh sử trước đó: %s"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            buildConfigField("String", "CHAT_SYSTEM_PROMPT", getPrompt("TRIAGE_MEDICAL_SYSTEM_PROMPT"))
            buildConfigField("String", "SUMMARY_SYSTEM_PROMPT", getPrompt("ANALYSIS_MEDICAL_SYSTEM_PROMPT"))
            buildConfigField("String", "SYMPTOM_CACHE_PROMPT", getPrompt("SYMPTOM_CACHE_PROMPT"))
            buildConfigField("String", "CONTEXT_LOCATION", getPrompt("CONTEXT_LOCATION"))
            buildConfigField("String", "CONTEXT_SYMPTOMS", getPrompt("CONTEXT_SYMPTOMS"))
            buildConfigField("String", "CONTEXT_SUMMARY", getPrompt("CONTEXT_SUMMARY"))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)

    // Firebase AI Logic (Gemini API for Android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.config)
    implementation(libs.firebase.database)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Location
    implementation("com.google.android.gms:play-services-location:21.2.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
