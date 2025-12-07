import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * HMAC-SHA256 Signature Generator for Irembo Notifications API
 * 
 * This script generates the HMAC signature required for authentication
 * Signature format: HMAC-SHA256(timestamp + "\n" + method + "\n" + path + "\n" + body)
 */

// Get variables from JMeter
def apiSecret = vars.get("api.secret")
def timestamp = System.currentTimeMillis()
def httpMethod = "POST"
def requestPath = "/api/notifications"
def requestBody = vars.get("request_body")

// Store timestamp for use in request headers
vars.put("timestamp", timestamp.toString())

// Build signature payload
def payload = timestamp + "\n" + httpMethod.toUpperCase() + "\n" + requestPath + "\n" + requestBody

// Generate HMAC-SHA256 signature
def algorithm = "HmacSHA256"
def mac = Mac.getInstance(algorithm)
def secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), algorithm)
mac.init(secretKeySpec)

def signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
def signature = Base64.getEncoder().encodeToString(signatureBytes)

// Store signature for use in request headers
vars.put("signature", signature)


