package com.dji.sample.component;

import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@Component
public class RsaUtil {
    // RSA 密钥长度（默认 2048）
    @Value("${rsa.key.size:2048}")
    private int rsaKeySize;

    // 存储生成的公钥和私钥
    private String publicKey;
    private String privateKey;

    // 项目启动时生成 RSA 密钥对
    @PostConstruct
    public void generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(rsaKeySize);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // 获取公钥和私钥，转 Base64 字符串存储（方便前端使用）
        RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = Base64.getEncoder().encodeToString(rsaPublicKey.getEncoded());
        this.privateKey = Base64.getEncoder().encodeToString(rsaPrivateKey.getEncoded());
    }

    // 获取公钥（提供给前端）
    public String getPublicKey() {
        return publicKey;
    }

    // 私钥解密（前端加密后的密码）
    public String decryptByPrivateKey(String encryptedData) {
        RSA rsa = new RSA(privateKey, null);
        // 解密并转 UTF-8 字符串
        return rsa.decryptStr(encryptedData, KeyType.PrivateKey);
    }

    // 公钥加密（测试用，前端实际用 JS 加密）
    public String encryptByPublicKey(String rawData) {
        RSA rsa = new RSA(null, publicKey);
        return rsa.encryptBase64(rawData, KeyType.PublicKey);
    }
}