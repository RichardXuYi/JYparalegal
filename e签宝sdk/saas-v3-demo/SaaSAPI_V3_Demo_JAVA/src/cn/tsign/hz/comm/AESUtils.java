/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package cn.tsign.hz.comm;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * AES对称加密工具类
 *
 * @author 澄泓
 * @date 2019年7月18日
 */
public class AESUtils {

    private static final String KEY_ALGORITHM = "AES";
    // AES/GCM加密算法，不用补位
    private static final String DEFAULT_CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * AES 加密操作
     *
     * @param content   待加密内容
     * @param AESSecret AES秘钥
     * @return 返回Base64转码后的加密数据
     */
    public static String encrypt(String content, String AESSecret) {
        try {
            Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.decodeBase64(AESSecret), KEY_ALGORITHM));
            byte[] iv = cipher.getIV();
            assert iv.length == 12;
            byte[] encryptData = cipher.doFinal(content.getBytes());
            assert encryptData.length == content.getBytes().length + 16;
            byte[] message = new byte[12 + content.getBytes().length + 16];
            System.arraycopy(iv, 0, message, 0, 12);
            System.arraycopy(encryptData, 0, message, 12, encryptData.length);
            return Base64.encodeBase64URLSafeString(message);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
                | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * AES 解密操作
     *
     * @param base64Content
     * @param AESSecret     AES秘钥
     * @return
     */
    public static String decrypt(String base64Content, String AESSecret) {
        byte[] content = Base64.decodeBase64(base64Content);
        if (content.length < 12 + 16)
            throw new IllegalArgumentException();
        GCMParameterSpec params = new GCMParameterSpec(128, content, 0, 12);
        try {
            Cipher cipher = Cipher.getInstance(DEFAULT_CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.decodeBase64(AESSecret), KEY_ALGORITHM), params);
            byte[] decryptData = cipher.doFinal(content, 12, content.length - 12);
            return new String(decryptData);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        // 待加密的字符串
        String s = "{\"name\":\"张三\",\"idNo\":\"320333xxxxxxx12522\"}";
        // 秘钥
        String pass = "D/RA+2esbSbfSVOQsTGlpg==";
        // 加密
        String encoded = encrypt(s, pass);
        System.out.println("加密之前：" + s);
        System.out.println("加密结果：" + encoded);
        System.out.println("解密结果：" + decrypt(encoded, pass));
    }
}
