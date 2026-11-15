import java.security.KeyStore
import java.security.PrivateKey
import java.util.Properties
import me.okonecny.gradlekeyring.PublicKeyringExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

apply(from = rootProject.file("scripts/credits.gradle"))

val localSigningProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

fun signingProperty(name: String): String? =
    providers.gradleProperty(name).orNull ?: localSigningProperties.getProperty(name)

val keyringSigningEnabled = when (val value = signingProperty("keyringSigning") ?: "false") {
    "true" -> true
    "false" -> false
    else -> throw GradleException("keyringSigning must be true or false, received '$value'.")
}

// AGP stores signing passwords in its model, so they must never enter the configuration cache.
@Suppress("DEPRECATION")
val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested
if (keyringSigningEnabled && configurationCacheRequested) {
    throw GradleException("Keyring signing requires --no-configuration-cache to avoid caching passwords.")
}

fun requiredSigningProperty(name: String): String = signingProperty(name)
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Set $name in local.properties or with -P$name before enabling keyringSigning.")

fun signingPassword(name: String): String {
    val keyring = rootProject.extensions.getByType(PublicKeyringExtension::class.java)
    return keyring.secrets[name]?.takeUnless { it == "<not set>" || it.isEmpty() }
        ?: throw GradleException(
            "Signing secret '$name' is unavailable. Run ./scripts/init-keyring-signing.sh " +
                "or ./gradlew --no-configuration-cache -PkeyringSigning=false :setSecretValue --name=$name."
        )
}

// Dedicated, opt-in installation/update sequence for live verification.
val updateTestPackage = providers.gradleProperty("updateTestPackage").orNull?.let {
    it.toBooleanStrictOrNull() ?: throw GradleException("updateTestPackage must be true or false.")
} ?: false
val updateTestVersion = if (updateTestPackage) {
    providers.gradleProperty("updateTestVersionCode").orNull?.toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException("Set a positive -PupdateTestVersionCode for the dedicated test package.")
} else 4

android {
    namespace = "top.ntutn.sonovelreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (updateTestPackage) "top.ntutn.sonovelreader.updatetest" else "top.ntutn.sonovelreader"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        minSdk = 29
        targetSdk = 37
        versionCode = updateTestVersion
        versionName = if (updateTestPackage) "1.0-update-test.$updateTestVersion" else "1.0.3"
        buildConfigField("String", "UPDATE_SOFTWARE", "\"${if (updateTestPackage) "so-novel-reader-update-test" else "so-novel-reader"}\"")
        manifestPlaceholders["appLabel"] = if (updateTestPackage) "拾光阅读·更新测试" else "@string/app_name"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (keyringSigningEnabled) {
        val keystore = rootProject.file(requiredSigningProperty("keyringStoreFile"))
        if (!keystore.isFile) {
            throw GradleException("Signing keystore does not exist: $keystore")
        }
        val alias = requiredSigningProperty("keyringKeyAlias")
        signingConfigs.create("keyringRelease") {
            storeFile = keystore
            keyAlias = alias
            storePassword = signingPassword("android_store_password")
            keyPassword = signingPassword("android_key_password")
        }
    }

    buildTypes {
        release {
            if (keyringSigningEnabled) {
                signingConfig = signingConfigs.getByName("keyringRelease")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

val releaseSigningConfig = android.buildTypes.getByName("release").signingConfig
tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Checks the release keystore, passwords, private key and certificate chain without building."
    notCompatibleWithConfigurationCache("Reads local signing credentials")
    doLast {
        val config = releaseSigningConfig
            ?: throw GradleException("Release signing is not configured. Enable keyringSigning first.")
        val keystore = config.storeFile ?: throw GradleException("Signing keystore is not configured.")
        val alias = config.keyAlias ?: throw GradleException("Signing key alias is not configured.")
        val storePassword = config.storePassword?.toCharArray()
            ?: throw GradleException("Keystore password is not configured.")
        val keyPassword = config.keyPassword?.toCharArray()
            ?: throw GradleException("Private key password is not configured.")
        try {
            val store = KeyStore.getInstance(keystore, storePassword)
            if (store.getKey(alias, keyPassword) !is PrivateKey) {
                throw GradleException("Signing alias '$alias' does not contain a private key.")
            }
            if (store.getCertificateChain(alias).isNullOrEmpty()) {
                throw GradleException("Signing alias '$alias' does not contain a certificate chain.")
            }
            logger.lifecycle("Release signing verified: keystore, private key and certificate chain are readable.")
        } finally {
            storePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.kmp.updater)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(project(":ptqflipper"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation(libs.epub4j.core) {
        exclude(group = "xmlpull")
    }
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
