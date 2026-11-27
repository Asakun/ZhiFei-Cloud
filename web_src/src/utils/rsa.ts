// src/utils/rsa.js
import JSEncrypt from 'jsencrypt'

// 后端提供的公钥 (通常是以 -----BEGIN PUBLIC KEY----- 开头的字符串)


/**
 * RSA 加密函数
 * @param {string} data - 需要加密的数据
 * @returns {string|boolean} - 加密后的 Base64 字符串，失败返回 false
 */
export function rsaEncrypt(data: string, PUBLIC_KEY: string) {
    const encryptor = new JSEncrypt()
    // 设置公钥
    encryptor.setPublicKey(PUBLIC_KEY)
    // 加密
    return encryptor.encrypt(data)
}

/**
 * RSA 解密函数 (前端通常只负责加密，极少需要解密，除非是双向通信)
 * @param {string} encryptedData 
 * @param {string} privateKey - 前端通常不存私钥，这里仅作演示
 */
export function rsaDecrypt(encryptedData: string, privateKey: string) {
    const decryptor = new JSEncrypt()
    decryptor.setPrivateKey(privateKey)
    return decryptor.decrypt(encryptedData)
}