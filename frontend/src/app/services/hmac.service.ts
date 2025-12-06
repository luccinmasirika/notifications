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
   * Generate HMAC-SHA256 signature for a request.
   * 
   * @param apiSecret The API secret (plain text)
   * @param timestamp Request timestamp (Unix milliseconds)
   * @param httpMethod HTTP method (GET, POST, PUT, DELETE, etc.)
   * @param requestPath Request path (e.g., "/api/notifications")
   * @param requestBody JSON body (empty string for GET requests)
   * @returns Base64-encoded HMAC-SHA256 signature
   */
  generateSignature(
    apiSecret: string,
    timestamp: number,
    httpMethod: string,
    requestPath: string,
    requestBody: string = ''
  ): string {
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

    // Build signature payload: timestamp + "\n" + method + "\n" + path + "\n" + body
    const payload = `${timestamp}\n${httpMethod}\n${requestPath}\n${requestBody}`;

    // Generate HMAC-SHA256 signature using Web Crypto API
    return this.generateHmacSha256(apiSecret, payload);
  }

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
   * Synchronous wrapper for HMAC generation (for compatibility).
   * Note: This uses a Promise internally but returns synchronously for simple cases.
   */
  private generateHmacSha256(secret: string, message: string): string {
    // For now, we'll use a synchronous approach with a library or make it async
    // Since Web Crypto API is async, we'll need to handle this differently
    // For immediate use, we can use a synchronous implementation
    
    // Using a simple synchronous HMAC implementation
    // Note: In production, consider using a library like crypto-js or making this async
    try {
      // Try to use Web Crypto API synchronously (not possible, so we'll need async)
      // For now, return a placeholder - we'll need to make the calling code async
      throw new Error('HMAC generation must be async - use generateSignatureAsync instead');
    } catch (e) {
      throw e;
    }
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
