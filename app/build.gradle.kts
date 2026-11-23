import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Supabase URL / anon key vengono inseriti nell'APK: l'utente non deve configurare nulla.
// Ordine di priorità: variabili d'ambiente (secret della CI) > local.properties > supabase.properties.
// La chiave anon/publishable è pubblica per design (la sicurezza è nelle policy RLS):
// può stare nel repository. MAI mettere qui la chiave service_role / secret.
fun props(name: String) = Properties().apply {
    val f = rootProject.file(name)
    if (f.exists()) f.inputStream().use { load(it) }
}
val localProps = props("local.properties")
val supabaseProps = props("supabase.properties")
fun cfg(key: String, env: String): String =
    (System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: supabaseProps.getProperty(key) ?: "").trim()
val supabaseUrl = cfg("supabase.url", "SUPABASE_URL").trim('"').trim()
val supabaseAnonKey = cfg("supabase.anonKey", "SUPABASE_ANON_KEY").trim('"').trim()
require(!supabaseAnonKey.contains('"') && !supabaseUrl.contains('"')) { "URL o chiave Supabase contengono virgolette" }
// le chiavi legacy sono JWT: il ruolo è nel payload (base64url)
val keyRole = runCatching {
    String(Base64.getUrlDecoder().decode(supabaseAnonKey.split('.')[1].trimEnd('=')))
}.getOrDefault("")
require(!supabaseAnonKey.startsWith("sb_secret_") && !keyRole.contains("service_role")) {
    "supabase.anonKey contiene una chiave segreta: usa la chiave anon / publishable"
}

// Firma release opzionale (keystore.properties o variabili d'ambiente in CI)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun ks(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

// Screenshot delle schermate con Robolectric: solo con SCREENSHOTS=1, perché
// richiedono di scaricare un runtime Android (~150 MB). La CI non ne ha bisogno.
val screenshots = System.getenv("SCREENSHOTS") == "1"

android {
    namespace = "com.nanobuo.myfifty"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nanobuo.myfifty"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        // Release: VERSION_NAME dal tag (es. 1.0.0). Altre build: 1.0.0-dev.<n>, considerate
        // più vecchie della release 1.0.0, così il controllo aggiornamenti propone la release.
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
            ?: "${providers.gradleProperty("appVersion").get()}-dev.${System.getenv("VERSION_CODE") ?: "0"}"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        // repository da cui scaricare gli aggiornamenti (GitHub Releases)
        buildConfigField("String", "UPDATE_REPO", "\"${System.getenv("GITHUB_REPOSITORY") ?: "Nanobuo/myFifty"}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    signingConfigs {
        val storeFilePath = ks("storeFile", "SIGNING_STORE_FILE")
        if (storeFilePath != null && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = ks("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = ks("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = ks("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    if (screenshots) {
        sourceSets.getByName("test").java.srcDir("src/screenshotTest/java")
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    if (screenshots) {
        testImplementation(libs.robolectric)
        testImplementation(platform(libs.androidx.compose.bom))
        testImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.test.manifest)
    }
}

android {
    lint {
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "ObsoleteSdkInt", "NewerVersionAvailable")
    }
}

// Un APK da distribuire senza i dati del server non avrebbe senso: blocca la build release.
val checkSupabaseConfig by tasks.registering {
    doLast {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) throw GradleException(
            "Configurazione Supabase mancante: compila supabase.properties (supabase.url e supabase.anonKey) " +
                "oppure imposta i secret SUPABASE_URL e SUPABASE_ANON_KEY. Vedi docs/SUPABASE_SETUP.md"
        )
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkSupabaseConfig) }
