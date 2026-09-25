package com.jyfc.cp.config;

import com.auth0.jwt.algorithms.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CP 的 passport 签名密钥（RSA-2048）。
 *
 * <p><b>为什么是 RS256 而不是 HS256</b>：数据平面（DP）只应拿到<b>公钥</b>就能验票。
 * 若用对称的 HS256，DP 持有校验密钥即等于持有签发能力——客户机器被拿下就能自造
 * "已付费/已授权"的 passport（docs/08 §1 威胁①）。因此私钥永不离开 CP，公钥经
 * {@code /cp/.well-known/jwks.json} 供 DP 离线验签。</p>
 *
 * <p>密钥来源按优先级：{@code cp.jwt.rsa.private-key}（PEM 或裸 base64 PKCS#8）→
 * {@code cp.jwt.rsa.private-key-path} → {@code cp.data.dir} 下已持久化的
 * {@code jwt-private.pem}/{@code jwt-public.pem} → 首次启动自动生成并落盘。
 * 生成后必须落盘，否则 CP 每次重启换钥，DP 缓存里的旧票全部失效、已签发票据作废。
 * 多实例部署必须显式配同一份 PEM。</p>
 */
@Component
public class CpRsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(CpRsaKeyProvider.class);

    private static final String PRIVATE_PEM = "jwt-private.pem";
    private static final String PUBLIC_PEM = "jwt-public.pem";
    private static final int KEY_SIZE = 2048;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String kid;

    public CpRsaKeyProvider(
            @Value("${cp.jwt.rsa.private-key:}") String configuredPrivateKey,
            @Value("${cp.jwt.rsa.private-key-path:}") String configuredPrivateKeyPath,
            @Value("${cp.jwt.rsa.public-key-path:}") String configuredPublicKeyPath,
            @Value("${cp.data.dir:}") String configuredDataDir,
            Environment environment) {
        try {
            Loaded loaded = load(configuredPrivateKey, configuredPrivateKeyPath,
                    configuredPublicKeyPath, configuredDataDir, isDev(environment));
            this.publicKey = loaded.publicKey;
            this.privateKey = loaded.privateKey;
            this.kid = computeKid(loaded.publicKey);
            log.info("CP passport 签名密钥就绪：RS256 kid={}（来源 {}）", kid, loaded.source);
        } catch (Exception e) {
            throw new IllegalStateException("CP 启动失败：无法准备 passport 签名密钥（RS256）。"
                    + "请配置 cp.jwt.rsa.private-key[-path]，或确保 cp.data.dir 可写。", e);
        }
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String kid() {
        return kid;
    }

    /** 签发用算法（同时带公钥，java-jwt 校验算法完整性）。 */
    public Algorithm signAlgorithm() {
        return Algorithm.RSA256(publicKey, privateKey);
    }

    /** 本节点的 JWKS 文档，供 DP 的 CpTokenVerifier 拉取。 */
    public Map<String, Object> jwks() {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", kid);
        key.put("n", toUnsignedBase64Url(publicKey.getModulus()));
        key.put("e", toUnsignedBase64Url(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(key));
    }

    // ==================== 载入 / 生成 ====================

    private record Loaded(RSAPublicKey publicKey, RSAPrivateKey privateKey, String source) {}

    private Loaded load(String pemConfig, String privateKeyPath, String publicKeyPath,
                        String dataDir, boolean isDev) throws Exception {
        if (!isBlank(pemConfig)) {
            RSAPrivateKey priv = privateKeyFrom(pemConfig);
            return new Loaded(derivePublicFrom(priv), priv, "配置 private-key");
        }
        if (!isBlank(privateKeyPath)) {
            RSAPrivateKey priv = readPrivateKey(Paths.get(privateKeyPath));
            RSAPublicKey pub = isBlank(publicKeyPath)
                    ? derivePublicFrom(priv)
                    : readPublicKey(Paths.get(publicKeyPath));
            return new Loaded(pub, priv, "配置 key-path");
        }

        Path dir = resolveDataDir(dataDir);
        Path privFile = dir.resolve(PRIVATE_PEM);
        Path pubFile = dir.resolve(PUBLIC_PEM);
        if (Files.exists(privFile)) {
            RSAPrivateKey priv = readPrivateKey(privFile);
            RSAPublicKey pub = Files.exists(pubFile) ? readPublicKey(pubFile) : derivePublicFrom(priv);
            return new Loaded(pub, priv, "已持久化密钥 " + dir);
        }

        KeyPair pair = generateKeyPair();
        persist(dir, privFile, pubFile, pair, isDev);
        return new Loaded((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), "首次生成于 " + dir);
    }

    private void persist(Path dir, Path privFile, Path pubFile, KeyPair pair, boolean isDev) {
        try {
            Files.createDirectories(dir);
            writePem(privFile, "PRIVATE KEY", pair.getPrivate().getEncoded());
            tryRestrictToOwner(privFile);
            writePem(pubFile, "PUBLIC KEY", pair.getPublic().getEncoded());
        } catch (IOException e) {
            if (!isDev) {
                throw new IllegalStateException("CP 启动失败：无法在 " + dir
                        + " 持久化签名密钥（非 dev profile 不允许每次重启换钥）。"
                        + "请设置可写的 CP_DATA_DIR，或注入 cp.jwt.rsa.private-key。", e);
            }
            log.warn("dev profile：签名密钥无法落盘（{}），本次仅在内存中使用，重启后旧票据将失效。",
                    e.getMessage());
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_SIZE);
        return generator.generateKeyPair();
    }

    private Path resolveDataDir(String dataDir) {
        if (!isBlank(dataDir)) {
            return Paths.get(dataDir.trim());
        }
        return Paths.get(System.getProperty("user.home"), ".jy-cp");
    }

    // ==================== PEM / DER 解析 ====================

    private RSAPrivateKey privateKeyFrom(String configValue) throws Exception {
        byte[] der = configValue.contains("-----BEGIN")
                ? decodePem(configValue)
                : Base64.getMimeDecoder().decode(configValue.trim());
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPrivateKey readPrivateKey(Path path) throws Exception {
        return privateKeyFrom(Files.readString(path, StandardCharsets.US_ASCII));
    }

    private RSAPublicKey readPublicKey(Path path) throws Exception {
        byte[] der = decodePem(Files.readString(path, StandardCharsets.US_ASCII));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    /** RSAPrivateCrtKey 自带模数与公钥指数，可直接推出公钥，免去强制配置公钥文件。 */
    private RSAPublicKey derivePublicFrom(RSAPrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof java.security.interfaces.RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("CP 的 passport 私钥必须是 RSAPrivateCrtKey 才能推出公钥用于 JWKS");
        }
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private byte[] decodePem(String pem) {
        String body = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private void writePem(Path path, String label, byte[] der) throws IOException {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        Files.writeString(path, "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n",
                StandardCharsets.US_ASCII);
    }

    /** Windows/非 POSIX 文件系统上静默跳过；私钥保护依赖部署侧目录权限。 */
    private void tryRestrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 见类注释：生产部署需自行保证 cp.data.dir 权限
        }
    }

    // ==================== 工具 ====================

    private String computeKid(RSAPublicKey key) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(key.getEncoded());
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 8; i++) {
            sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
            sb.append(Character.forDigit(digest[i] & 0xF, 16));
        }
        return sb.toString();
    }

    private String toUnsignedBase64Url(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsignedBytes(value));
    }

    /** JWK 的 n/e 是"无符号最小"大端表示，需去掉 BigInteger 的符号位前导 0x00。 */
    private byte[] unsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isDev(Environment environment) {
        return List.of(environment.getActiveProfiles()).contains("dev");
    }
}
