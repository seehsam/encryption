import org.json.JSONArray;
import org.json.JSONObject;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Seehsam {

    private static  int RSA_KEY_SIZE = 4096;
    private static int AES_KEY_SIZE = 256;
    private static int IV_KEY_SIZE = 16;
    private static int HASH_KEY_SIZE = 16;

    /**
     * Generiert ein RSA Schlüsselpaar mit einem Public und Private-Key
     * Der Private-Key wird mit der Passphrase verschlüsselt
     * Schlüssellänge: 512 (RSA_KEY_SIZE)
     * @param passphrase Kennwort des Nutzers
     * @return JSONObject with public and private key
     */
    private static JSONObject generateKeyPair(String passphrase) throws Exception{
        KeyPair keyPair;
        KeyPairGenerator keygenerator;
        JSONObject JSONkeyPairObject = new JSONObject();
        try {
            keygenerator = KeyPairGenerator.getInstance("RSA");
            keygenerator.initialize(RSA_KEY_SIZE);
            keyPair = keygenerator.genKeyPair();
            byte[] encodedPrivateKey = keyPair.getPrivate().getEncoded();
            String b64private = Base64.getEncoder().encodeToString(encodedPrivateKey);
            JSONkeyPairObject.put("private",encryptString(b64private,passphrase));
            byte[] encodedPublicKey = keyPair.getPublic().getEncoded();
            String b64public = Base64.getEncoder().encodeToString(encodedPublicKey);
            JSONkeyPairObject.put("public",b64public);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return JSONkeyPairObject;
    }

    /**
     * Generiert einen zufälligen Documentkey
     * Schlüssellänge 256 Bit (AES_KEY_SIZE)
     * @return  Zufälliger DocumentKey als Base64 String
     */
    private static String generateRandomDocumentKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        SecureRandom secureRandom = new SecureRandom();
        int keyBitSize = AES_KEY_SIZE;
        keyGenerator.init(keyBitSize, secureRandom);
        Key secretKey = keyGenerator.generateKey();
        byte[] b = secretKey.getEncoded();
        BASE64Encoder myEncoder = new BASE64Encoder();
        return myEncoder.encode(b);
    }

    /**
     * Verschluesselt den DocumentKey mit allen Public-Keys aus dem hasAccess Feld
     *
     * @param documentKey Schlüssel der den Vertrag verschlüsselt
     * @param hasAcc      JSONArray mit allen berechtigten Nutzern
     * @return BASE64 String
     */
    private String encryptDocumentKey(String documentKey, JSONArray hasAcc) throws Exception {
        JSONArray docKeys = new JSONArray();
        BASE64Encoder myEncoder = new BASE64Encoder();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        for (int i = 0; i < hasAcc.length(); i++) {
            // Verschluesseln
            Cipher cipher = Cipher.getInstance("RSA");
            //Convert Base64 Public-Key String to PublicKey
            String base64public = hasAcc.getJSONObject(i).getJSONObject("encryption").get("public").toString();
            byte[] publicBytes = Base64.getDecoder().decode(base64public);
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(publicBytes);
            PublicKey pubKey = keyFactory.generatePublic(pubKeySpec);
            //Encrypt
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);
            byte[] encrypted = cipher.doFinal(documentKey.getBytes());
            // bytes zu Base64-String konvertieren
            String geheim = myEncoder.encode(encrypted);
            JSONObject docKey = new JSONObject();
            docKey.put(hasAcc.getJSONObject(i).get("name").toString(), geheim);
            docKeys.put(docKey);
        }
        return myEncoder.encode(docKeys.toString().getBytes());
    }

    /**
     * Entschluesselt einen BASE64 kodierten DocumentKey mittels dem user und dessen Private-Key
     * Der Private-Key wird mit der passphrase entschlüsselt
     * @param documentKeyEnc BASE64 kodierter Text
     * @param user JSONObject eines Users
     * @return DocumentKey im Klartext
     */
    private String decryptDocumentKey(String documentKeyEnc, JSONObject user, String passphrase) throws Exception {
        String username = user.get("name").toString();
        String encDocKeyUser;
        byte[] plainDocKey;
        byte[] cipherData;
        String finalCiper = null;
        BASE64Decoder myDecoder = new BASE64Decoder();
        byte[] plainDoc = myDecoder.decodeBuffer(documentKeyEnc);
        JSONArray jsa = new JSONArray(new String(plainDoc));
        for (int i = 0; i < jsa.length(); i++) { //Search for name!
            if(jsa.getJSONObject(i).has(username)) {
                encDocKeyUser  = jsa.getJSONObject(i).get(username).toString();
                plainDocKey = myDecoder.decodeBuffer(encDocKeyUser);
                Cipher cipher = Cipher.getInstance("RSA");
                String base64private = decryptString(user.getJSONObject("encryption").get("private").toString(),passphrase);
                byte[] privateBytes = Base64.getDecoder().decode(base64private);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPrivateKey privKey = (RSAPrivateKey) kf.generatePrivate(spec);
                cipher.init(Cipher.DECRYPT_MODE, privKey);
                cipherData = cipher.doFinal(plainDocKey);
                finalCiper = new String(cipherData);
            }
        }
        return finalCiper;
    }


    private static String encryptString(String plainText, String key) throws Exception {
        byte[] clean = plainText.getBytes();

        // Generating IV.
        byte[] iv = new byte[IV_KEY_SIZE];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        // Hashing key.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(key.getBytes("UTF-8"));
        byte[] keyBytes = new byte[HASH_KEY_SIZE];
        System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        // Encrypt.
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
        byte[] encrypted = cipher.doFinal(clean);

        // Combine IV and encrypted part.
        byte[] encryptedIVAndText = new byte[IV_KEY_SIZE + encrypted.length];
        System.arraycopy(iv, 0, encryptedIVAndText, 0, IV_KEY_SIZE);
        System.arraycopy(encrypted, 0, encryptedIVAndText, IV_KEY_SIZE, encrypted.length);

        return Base64.getEncoder().encodeToString(encryptedIVAndText);
    }

    private static String decryptString(String cipherText, String key) throws Exception {
        byte[] encryptedIvTextBytes = Base64.getDecoder().decode(cipherText);

        // Extract IV.
        byte[] iv = new byte[IV_KEY_SIZE];
        System.arraycopy(encryptedIvTextBytes, 0, iv, 0, iv.length);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        // Extract encrypted part.
        int encryptedSize = encryptedIvTextBytes.length - IV_KEY_SIZE;
        byte[] encryptedBytes = new byte[encryptedSize];
        System.arraycopy(encryptedIvTextBytes, IV_KEY_SIZE, encryptedBytes, 0, encryptedSize);

        // Hash key.
        byte[] keyBytes = new byte[HASH_KEY_SIZE];
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(key.getBytes());
        System.arraycopy(md.digest(), 0, keyBytes, 0, keyBytes.length);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        // Decrypt.
        Cipher cipherDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        byte[] decrypted = cipherDecrypt.doFinal(encryptedBytes);

        return new String(decrypted);
    }




    public static void main(String[] args) throws Exception {
        //Init Seehsam
        Seehsam sam = new Seehsam();

        //Init 2 User -> Peter & Fred
        JSONObject peter = new JSONObject();
        JSONObject fred = new JSONObject();
        JSONObject petKeyPair = generateKeyPair("vpasswordpasswordvpasswordpassword");
        peter.put("encryption" , petKeyPair);
        peter.put("name" , "Peter");
        JSONObject freKeyPair = generateKeyPair("vpasswordpasswordvpasswordpassword");
        fred.put("encryption" , freKeyPair);
        fred.put("name" , "Fred");

        //Init data
        JSONArray data = new JSONArray();
        JSONObject secretStuff = new JSONObject();
        secretStuff.put("cash","10000 Euro");
        secretStuff.put("time", 2);
        JSONObject secondSecretStuff = new JSONObject();
        secondSecretStuff.put("log","This is a long string with informations");
        secondSecretStuff.put("protocol","This is the protocol, im very happy");
        data.put(secretStuff);
        data.put(secondSecretStuff);

        //Init has access
        JSONArray hasAccess = new JSONArray();
        hasAccess.put(peter);
        hasAccess.put(fred);



        //Encrypt Private-Key
        System.out.println("EncPrivateKey Peter in Base64: ");
        System.out.println(peter.getJSONObject("encryption").get("private").toString());
        System.out.println();


        //Decrypted Private-Key
        System.out.println("Decrypted PrivateKey Peter in Base64: ");
        System.out.println(sam.decryptString(peter.getJSONObject("encryption").get("private").toString(),"vpasswordpasswordvpasswordpassword"));
        System.out.println();

        //generate documentKey
        String randomDoc = generateRandomDocumentKey();

        //Plain documentKey
        System.out.println("Documentkey Klartext: ");
        System.out.println(randomDoc);
        System.out.println();

        //Encrypted Documentkey hasAccess
        String encDocKey =  sam.encryptDocumentKey(randomDoc,hasAccess);
        System.out.println("Encrypted Documentkey in Base64: ");
        System.out.println(encDocKey);
        System.out.println();

        //Decrypted DocumentKey Fred
        String decDocKeyFred = sam.decryptDocumentKey(encDocKey,fred,"vpasswordpasswordvpasswordpassword");
        System.out.println();
        System.out.println("Decrypted DocumentKey Fred: ");
        System.out.println(decDocKeyFred);

        //DocumentKey entschlüsseln Peter
        String decDocKeyPeter = sam.decryptDocumentKey(encDocKey,peter,"vpasswordpasswordvpasswordpassword");
        System.out.println();
        System.out.println("Decrypted DocKeyPeter: ");
        System.out.println(decDocKeyPeter);

        //Plain Data
        System.out.println();
        System.out.println("Plain data: ");
        System.out.println(data);

        //Encrypted data with documentKey Fred
        String dataCipherFred = sam.encryptString(data.toString(),decDocKeyFred);
        System.out.println();
        System.out.println("Encrypted data with documentKey Fred: ");
        System.out.println(dataCipherFred);

        //Encrypted data with documentKey Peter
        String dataCipherPeter = sam.encryptString(data.toString(),decDocKeyPeter);
        System.out.println();
        System.out.println("Encrypted data with documentKey Peter: ");
        System.out.println(dataCipherPeter);

        //Decrypted data Fred
        String plainDataFred = sam.decryptString(dataCipherFred,decDocKeyFred);
        System.out.println();
        System.out.println("Decrypted data Fred: ");
        System.out.println(plainDataFred);

        //Decrypted data Peter
        String plainDataPeter = sam.decryptString(dataCipherPeter,decDocKeyPeter);
        System.out.println();
        System.out.println("Decrypted data Peter: ");
        System.out.println(plainDataPeter);
    }
}