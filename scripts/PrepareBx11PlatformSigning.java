import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;

/** Import explicitly supplied AOSP test material locally; never read a private key from an APK. */
class PrepareBx11PlatformSigning {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
            "Usage: java scripts/PrepareBx11PlatformSigning.java platform.pk8 platform.x509.pem");
        Path root = Path.of("").toAbsolutePath().normalize();
        Path output = root.resolve(".private/signing/bx11-platform.p12");
        Path config = root.resolve("platform-signing.properties");
        if (Files.exists(output) || Files.exists(config)) throw new IllegalStateException(
            "Platform signing already exists; keep it, do not overwrite it");
        var certificate = CertificateFactory.getInstance("X.509").generateCertificate(
            new java.io.ByteArrayInputStream(Files.readAllBytes(Path.of(args[1]))));
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(certificate.getEncoded()));
        if (!fingerprint.equals("c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"))
            throw new IllegalArgumentException("Certificate does not match verified BX11 platform");
        byte[] encoded = Files.readAllBytes(Path.of(args[0]));
        var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        java.util.Arrays.fill(encoded, (byte) 0);
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(nonce);
        byte[] proof = signature.sign();
        signature.initVerify(certificate);
        signature.update(nonce);
        if (!signature.verify(proof)) throw new IllegalArgumentException("Key/certificate mismatch");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String password = Base64.getEncoder().encodeToString(random);
        var store = KeyStore.getInstance("PKCS12");
        store.load(null, password.toCharArray());
        store.setKeyEntry("bx11-platform", key, password.toCharArray(),
            new java.security.cert.Certificate[] {certificate});
        Files.createDirectories(output.getParent());
        try (var stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            store.store(stream, password.toCharArray());
        }
        var properties = new Properties();
        properties.setProperty("storeFile", root.relativize(output).toString().replace('\\', '/'));
        properties.setProperty("storePassword", password);
        properties.setProperty("keyAlias", "bx11-platform");
        properties.setProperty("keyPassword", password);
        try (var stream = Files.newOutputStream(config, StandardOpenOption.CREATE_NEW)) {
            properties.store(stream, "Local BX11 platform test signing; never commit");
        }
        System.out.println("Verified platform SHA-256: " + fingerprint);
        System.out.println("Local signing configured; no installation performed.");
    }
}
