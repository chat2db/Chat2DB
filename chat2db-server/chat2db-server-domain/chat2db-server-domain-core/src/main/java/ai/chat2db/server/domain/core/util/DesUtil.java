package ai.chat2db.server.domain.core.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

/**
 * @author moji
 * @version DESUtil.java, v 0.1 December 26, 2022 19:54 moji Exp $
 * @date 2022/12/26
 */
public class DesUtil {

    /**
     * CFB
     */
    public static final String CFB = "CFB";

    /**
     * OFB
     */
    public static final String OFB = "OFB";

    /**
     * CBC
     */
    public static final String CBC = "CBC";

    /**
     * iv vector
     */
    private static final byte[] DESIV = { (byte) 0xCE, (byte) 0x35, (byte) 0x5,
        (byte) 0xD, (byte) 0x98, (byte) 0x91, (byte) 0x8, (byte) 0xA };

    /**
     * AlgorithmParameterSpec
     */
    private static AlgorithmParameterSpec IV = null;

    /**
     * SHA1PRNG
     */
    private static final String SHA1PRNG = "SHA1PRNG";

    /**
     * DES default mode
     */
    private static final String DES = "DES";

    /**
     * CBC encryption mode
     */
    private static final String DES_CBC_PKCS5PADDING = "DES/CBC/PKCS5Padding";

    /**
     * OFB encryption mode
     */
    private static final String DES_OFB_PKCS5PADDING = "DES/OFB/PKCS5Padding";

    /**
     * CFB encryption mode
     */
    private static final String DES_CFB_PKCS5_PADDING = "DES/CFB/PKCS5Padding";

    /**
     * encryption mode
     */
    private static final int ENCRYPT_MODE = 1;

    /**
     * des key
     */
    public static final String DES_KEY = "dbhub";

    /**
     * Decryption mode
     */
    private static final int DECRYPT_MODE = 2;

    /**
     * private key
     */
    private Key key;

    public DesUtil(String str) {
        getKey(str);
    }

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    /**
     * Get key through private key
     * @param secretKey private key
     * @author sucb
     * @date 2017年2月28日下午1:17:58
     */
    public void getKey(String secretKey) {
        try {
            SecureRandom secureRandom = SecureRandom.getInstance(SHA1PRNG);
            secureRandom.setSeed(secretKey.getBytes());
            KeyGenerator generator = null;
            try {
                generator = KeyGenerator.getInstance(DES);
            } catch (NoSuchAlgorithmException e) {
            }
            generator.init(secureRandom);
            IV = new IvParameterSpec(DESIV);


            this.key = generator.generateKey();
            generator = null;

        } catch (Exception e) {
            throw new RuntimeException("Error in getKey(String secretKey), Cause: " + e);
        }
    }

    /**
     * String des encryption
     * @param data String that needs to be encrypted
     * @param encryptType encryption mode (ECB/CBC/OFB/CFB)
     * @return Encryption result
     * @throws Exception exception
     * @author sucb
     * @date March 2, 2017 7:47:37 pm
     */
    public String encrypt(String data, String encryptType) throws Exception {
        Cipher cipher = getPattern(encryptType, ENCRYPT_MODE);
        byte[] pasByte = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(pasByte);
    }

    /**
     * String decryption
     * @param data The string that needs to be decrypted
     * @param decryptType  Decryption mode (ECB/CBC/OFB/CFB)
     * @return Decryption result
     * @throws Exception exception
     * @author sucb
     * @date March 2, 2017 7:48:21 pm
     */
    public String decrypt(String data, String decryptType) throws Exception {
        if (StringUtils.isBlank(data)) {
            return Strings.EMPTY;
        }
        Cipher cipher = getPattern(decryptType, DECRYPT_MODE);
        byte[] pasByte = cipher.doFinal(Base64.getDecoder().decode(data));
        return new String(pasByte, "UTF-8");
    }

    /**
     * Initialize cipher
     * @param type  Encryption/decryption mode (ECB/CBC/OFB/CFB)
     * @param cipherMode cipher working mode 1: encryption; 2: decryption
     * @return cipher
     * @throws Exception exception
     * @author sucb
     * @date March 2, 2017 7:49:16 pm
     */
    private Cipher getPattern(String type, int cipherMode) throws Exception {
        Cipher cipher;
        switch (type){
            case CBC :
                cipher = Cipher.getInstance(DES_CBC_PKCS5PADDING);
                cipher.init(cipherMode, key, IV);
                break;
            case OFB :
                cipher = Cipher.getInstance(DES_OFB_PKCS5PADDING);
                cipher.init(cipherMode, key, IV);
                break;
            case CFB :
                cipher = Cipher.getInstance(DES_CFB_PKCS5_PADDING);
                cipher.init(cipherMode, key, IV);
                break;
            default :
                cipher = Cipher.getInstance(DES);
                cipher.init(cipherMode, key);
                break;
        }
        return cipher;
    }

    /**
     * The file file is encrypted and saved in the target file destFile.
     * @param file The file to be encrypted such as c:/test/file.txt
     * @param destFile The name of the file stored after encryption, such as c:/ encrypted file .txt
     * @param encryptType encryption mode (ECB/CBC/OFB/CFB)
     * @return Encryption result 0: Abnormal 1: Encryption successful; 5: File to be encrypted not found
     * @author sucb
     * @date March 2, 2017 7:56:08 pm
     */
    public int encryptFile(String file, String destFile, String encryptType) {
        int result = 0;
        try {
            Cipher cipher = getPattern(encryptType, ENCRYPT_MODE);
            InputStream is = new FileInputStream(file);
            OutputStream out = new FileOutputStream(destFile);
            CipherInputStream cis = new CipherInputStream(is, cipher);
            byte[] buffer = new byte[1024];
            int r;
            while ((r = cis.read(buffer)) > 0) {
                out.write(buffer, 0, r);
            }
            cis.close();
            is.close();
            out.close();
            result = 1;
        } catch (FileNotFoundException e) {
            result = 5;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * The file file is decrypted and saved in the target file destFile.
     * @param file The file to be decrypted such as c:/test/file.txt
     * @param destFile The name of the file stored after decryption, such as c:/ decrypted file .txt
     * @param decryptType Decryption mode (ECB/CBC/OFB/CFB)
     * @return Decryption result 0: decryption abnormal; 1: decryption normal; 5: file to be decrypted not found
     * @author sucb
     * @date March 2, 2017 7:58:56 pm
     */
    public int decryptFile(String file, String destFile, String decryptType) {
        int result = 0;
        try {
            Cipher cipher = getPattern(decryptType, DECRYPT_MODE);
            InputStream is = new FileInputStream(file);
            OutputStream out = new FileOutputStream(destFile);
            CipherOutputStream cos = new CipherOutputStream(out, cipher);
            byte[] buffer = new byte[1024];
            int r;
            while ((r = is.read(buffer)) >= 0) {
                cos.write(buffer, 0, r);
            }
            cos.close();
            out.close();
            is.close();
            result = 1;
        }catch (FileNotFoundException e) {
            result = 5;
        }  catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
