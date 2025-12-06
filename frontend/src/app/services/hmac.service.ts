import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class HmacService {

  private async generateHmacSha256Async(secret: string, message: string): Promise<string> {
    const encoder = new TextEncoder();
    const keyData = encoder.encode(secret);
    const messageData = encoder.encode(message);

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

    const signature = await crypto.subtle.sign('HMAC', cryptoKey, messageData);

    return btoa(String.fromCharCode(...new Uint8Array(signature)));
  }

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

    const payload = `${timestamp}\n${httpMethod}\n${requestPath}\n${requestBody}`;

    return await this.generateHmacSha256Async(apiSecret, payload);
  }

  getCurrentTimestamp(): number {
    return Date.now();
  }
}
