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

// 本地密码与密钥不进入版本库；CI 仍可通过原有环境变量签名。
val localSigning = Properties().apply {
    rootProject.file("release-signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingInput(environment: String, property: String): String =
    providers.environmentVariable(environment).orNull ?: localSigning.getProperty(property, "")
val signingPath = signingInput("ANDROID_KEYSTORE_PATH", "storeFile")
val signingStore = rootProject.file(signingPath.ifEmpty { "missing-release-keystore.jks" })
val signingPassword = signingInput("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val signingAlias = signingInput("ANDROID_KEY_ALIAS", "keyAlias")
val signingKeyPassword = signingInput("ANDROID_KEY_PASSWORD", "keyPassword")
val expectedSigner = signingInput("LYNKPLAY_SIGNER_SHA256", "signerSha256")

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
            storeFile = signingStore
            storePassword = signingPassword
            keyAlias = signingAlias
            keyPassword = signingKeyPassword
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
        create("carTest") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            versionNameSuffix = "-lynk06-test"
        }
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

val verifyLynkPlaySigning by tasks.registering {
    group = "verification"
    description = "检查长期 APK 签名与本地固定证书指纹。"
    val keyStoreFile = signingStore
    val storePass = signingPassword
    val alias = signingAlias
    val keyPass = signingKeyPassword
    val expected = expectedSigner.lowercase()
    doLast {
        check(keyStoreFile.isFile && expected.matches(Regex("[0-9a-f]{64}"))) {
            "缺少签名配置；请先运行 uv run scripts/initialize_lynkplay_signing.py，或配置环境变量及 LYNKPLAY_SIGNER_SHA256"
        }
        val keyStore = KeyStore.getInstance(keyStoreFile, storePass.toCharArray())
        check(keyStore.getKey(alias, keyPass.toCharArray()) is PrivateKey) {
            "APK 签名别名没有私钥"
        }
        val certificate = checkNotNull(keyStore.getCertificate(alias)) { "APK 签名证书缺失" }
        val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actual == expected) { "APK 签名指纹不匹配；请恢复原签名，勿重新生成密钥" }
    }
}
tasks.matching { it.name == "preCarTestBuild" }.configureEach {
    dependsOn(verifyStandaloneAuthentication, verifyLynkPlaySigning)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyStandaloneAuthentication, verifyLynkPlaySigning)
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}
