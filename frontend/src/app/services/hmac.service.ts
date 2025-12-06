import { Injectable } from '@angular/core';

/**
 * Service for generating HMAC-SHA256 signatures for API authentication.
 * 
 * Signature format: HMAC-SHA256(api_secret, timestamp + "\n" + HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + JSON_BODY)
 */
@Injectable({
  providedIn: 'root'
})
export class HmacService {

  /**
   * Generate HMAC-SHA256 signature using Web Crypto API.
   * 
   * @param secret The secret key
   * @param message The message to sign
   * @returns Base64-encoded signature
   */
  private async generateHmacSha256Async(secret: string, message: string): Promise<string> {
    const encoder = new TextEncoder();
    const keyData = encoder.encode(secret);
    const messageData = encoder.encode(message);

    // Import the key
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      keyData,
      {
        name: 'HMAC',
        hash: 'SHA-256'
      },
      false,
      ['sign']
    );

    // Sign the message
    const signature = await crypto.subtle.sign('HMAC', cryptoKey, messageData);

    // Convert to base64
    return btoa(String.fromCharCode(...new Uint8Array(signature)));
  }

  /**
   * Generate HMAC-SHA256 signature asynchronously.
   * 
   * @param apiSecret The API secret
   * @param timestamp Request timestamp (Unix milliseconds)
   * @param httpMethod HTTP method
   * @param requestPath Request path
   * @param requestBody JSON body
   * @returns Promise resolving to Base64-encoded signature
   */
  async generateSignatureAsync(
    apiSecret: string,
    timestamp: number,
    httpMethod: string,
    requestPath: string,
    requestBody: string = ''
  ): Promise<string> {
    if (!apiSecret || !apiSecret.trim()) {
      throw new Error('API secret cannot be null or blank');
    }
    if (!httpMethod || !httpMethod.trim()) {
      throw new Error('HTTP method cannot be null or blank');
    }
    if (!requestPath) {
      throw new Error('Request path cannot be null');
    }
    if (requestBody === null) {
      requestBody = '';
    }

    // Build signature payload
    const payload = `${timestamp}\n${httpMethod}\n${requestPath}\n${requestBody}`;

    // Generate HMAC-SHA256 signature
    return await this.generateHmacSha256Async(apiSecret, payload);
  }

  /**
   * Get current timestamp in milliseconds (Unix epoch).
   */
  getCurrentTimestamp(): number {
    return Date.now();
  }
}
