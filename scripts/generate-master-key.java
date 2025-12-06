import java.security.KeyGenerator;
import java.util.Base64;
import javax.crypto.SecretKey;

/**
 * Utility class to generate a master key for API secret encryption.
 * 
 * Usage:
 *   javac -cp . scripts/generate-master-key.java
 *   java -cp . GenerateMasterKey
 * 
 * Or use the static method CryptoService.generateMasterKeyBase64()
 */
public class GenerateMasterKey {
    public static void main(String[] args) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256); // AES-256
            SecretKey key = keyGenerator.generateKey();
            String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
            
            System.out.println("✅ Master key generated successfully!");
            System.out.println();
            System.out.println("Add this to your environment variables:");
            System.out.println("  export APP_CRYPTO_MASTER_KEY=\"" + base64Key + "\"");
            System.out.println();
            System.out.println("Or add to docker-compose.yml:");
            System.out.println("  APP_CRYPTO_MASTER_KEY: \"" + base64Key + "\"");
            System.out.println();
            System.out.println("⚠️  IMPORTANT: Store this key securely. It cannot be recovered!");
            System.out.println("⚠️  If you lose this key, all encrypted API secrets will be unrecoverable!");
            System.out.println();
            System.out.println("For production, use a secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.)");
        } catch (Exception e) {
            System.err.println("Error generating master key: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

