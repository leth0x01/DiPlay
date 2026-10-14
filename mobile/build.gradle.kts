import java.util.Properties
import java.security.KeyStore
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.SecureRandom
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

// 官方写法：项目根目录的 keystore.properties 描述签名，密钥库放在仓库内的 keystore/。
// 两者都不进入版本库；CI 可通过环境变量覆盖。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}
fun keystoreInput(environment: String, property: String): String =
    providers.environmentVariable(environment).orNull ?: keystoreProperties.getProperty(property, "")
val platformStore = rootProject.file(keystoreInput("LYNKPLAY_PLATFORM_KEYSTORE", "storeFile")
    .ifEmpty { "missing-keystore.p12" })
val platformStorePassword = keystoreInput("LYNKPLAY_PLATFORM_STORE_PASSWORD", "storePassword")
val platformAlias = keystoreInput("LYNKPLAY_PLATFORM_ALIAS", "keyAlias")
val platformKeyPassword = keystoreInput("LYNKPLAY_PLATFORM_KEY_PASSWORD", "keyPassword")

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.leth0x01.lynkplay"
        minSdk = 25
        targetSdk = 37
        versionCode = providers.gradleProperty("lynkplayVersionCode").get().toInt()
        versionName = providers.gradleProperty("lynkplayVersionName").get()

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = platformStore
            storePassword = platformStorePassword
            keyAlias = platformAlias
            keyPassword = platformKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".hudtest"
            versionNameSuffix = "-hud-test"
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // 正式版与车测包使用同一构建，仪器 CarPlay 地图依赖 UID 1000。
    sourceSets {
        getByName("release").manifest.srcFile("src/systemUid/AndroidManifest.xml")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.app.projected)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() in 1..16384 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
        // 只验证格式与配对；这不证明 Apple 信任该实验性身份。
        val encodedKey = directory.resolve("offline-mfi/identity.pk8").readBytes()
        val key = try {
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        } finally { encodedKey.fill(0) }
        val certificates = directory.resolve("offline-mfi/certificate.p7b").inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificates(it)
        }
        check(certificates.size == 1) { "认证文件必须包含一个配件证书" }
        val publicKey = certificates.single().publicKey as? ECPublicKey
            ?: error("认证证书必须使用 EC 公钥")
        check(publicKey.params.order.toString(16) ==
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551") {
            "认证证书必须使用 P-256"
        }
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signature = Signature.getInstance("NONEwithECDSA")
        signature.initSign(key)
        signature.update(challenge)
        val proof = signature.sign()
        signature.initVerify(publicKey)
        signature.update(challenge)
        check(signature.verify(proof)) { "认证密钥与证书不匹配" }
    }
}

val verifyBx11PlatformSigning by tasks.registering {
    group = "verification"
    description = "Verify the BX11/IHU514G platform certificate before building a system UID APK."
    val keyStoreFile = platformStore
    val storePass = platformStorePassword
    val alias = platformAlias
    val keyPass = platformKeyPassword
    doLast {
        check(keyStoreFile.isFile) { "Missing keystore.properties or LYNKPLAY_PLATFORM_KEYSTORE" }
        val keyStore = KeyStore.getInstance(keyStoreFile, storePass.toCharArray())
        check(keyStore.getKey(alias, keyPass.toCharArray()) is PrivateKey) { "Platform signing alias has no private key" }
        val cert = checkNotNull(keyStore.getCertificate(alias))
        val actual = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actual == "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8") {
            "Platform certificate does not match the verified BX11 firmware"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyStandaloneAuthentication, "verifyBx11PlatformSigning")
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }

tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}
