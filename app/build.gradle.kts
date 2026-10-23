import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val releaseSigningKeys = listOf("SWIPEMETER_KEYSTORE", "SWIPEMETER_STORE_PASSWORD", "SWIPEMETER_KEY_ALIAS", "SWIPEMETER_KEY_PASSWORD")
val releaseSigningValues = releaseSigningKeys.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigningValues.values.any { it != null }
if (hasReleaseSigning) {
    val missing = releaseSigningValues.filterValues { it.isNullOrBlank() }.keys
    require(missing.isEmpty()) { "Missing release signing environment variables: ${missing.joinToString()}" }
    require(rootProject.file(releaseSigningValues.getValue("SWIPEMETER_KEYSTORE")!!).isFile) {
        "The configured release keystore does not exist."
    }
}

android {
    namespace = "com.bagimliligim.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.bagimliligim.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0.0"
        val backendUrl = providers.environmentVariable("BAGIMLILIGIM_BACKEND_URL").orElse("").get().trimEnd('/')
        val publicKey = providers.environmentVariable("BAGIMLILIGIM_PUBLIC_KEY").orElse("").get()
        require(backendUrl.isEmpty() || backendUrl.matches(Regex("https://[a-z0-9-]+\\.supabase\\.co")))
        require(publicKey.matches(Regex("[A-Za-z0-9_.-]*")))
        require(backendUrl.isEmpty() == publicKey.isEmpty()) { "Configure both the backend URL and its public client key." }
        if (publicKey.isNotEmpty()) {
            val isPublishable = publicKey.startsWith("sb_publishable_") && publicKey.length > 30
            val isAnonJwt = runCatching {
                val parts = publicKey.split('.')
                parts.size == 3 && Regex("\"role\"\\s*:\\s*\"anon\"")
                    .containsMatchIn(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
            }.getOrDefault(false)
            require(isPublishable || isAnonJwt) { "Only a publishable or anon key may be included in the Android app." }
        }
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "PUBLIC_KEY", "\"$publicKey\"")
        val googleClientId = providers.environmentVariable("BAGIMLILIGIM_GOOGLE_WEB_CLIENT_ID").orElse("").get()
        require(googleClientId.isEmpty() || googleClientId.matches(Regex("[0-9]+-[a-z0-9]+\\.apps\\.googleusercontent\\.com"))) {
            "Use the public Google Web OAuth client ID, never a client secret."
        }
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) create("production") {
            storeFile = rootProject.file(releaseSigningValues.getValue("SWIPEMETER_KEYSTORE")!!)
            storePassword = releaseSigningValues.getValue("SWIPEMETER_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("SWIPEMETER_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("SWIPEMETER_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
        }
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
room { schemaDirectory("$projectDir/schemas") }

val verifyPrivacyManifests = tasks.register("verifyPrivacyManifests") {
    group = "verification"
    description = "Checks the explicit V2 permission allowlist in every merged manifest."
}
androidComponents.onVariants { variant ->
    val manifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
    val verify = tasks.register("verify${variant.name.replaceFirstChar { it.uppercaseChar() }}PrivacyManifest") {
        group = "verification"
        inputs.file(manifest)
        doLast {
            val manifestText = manifest.get().asFile.readText()
            val allowed = setOf("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS", "${variant.applicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            val requested = Regex("<uses-permission(?:-sdk-23)?\\b[^>]*android:name=\"([^\"]+)\"")
                .findAll(manifestText).map { it.groupValues[1] }.toSet()
            check(requested.all { it in allowed }) {
                "Unexpected permissions in ${variant.name}: ${requested - allowed}"
            }
        }
    }
    verifyPrivacyManifests.configure { dependsOn(verify) }
}
tasks.named("check").configure { dependsOn(verifyPrivacyManifests) }

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.tooling)
    debugImplementation(libs.androidx.compose.test.manifest)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
}
