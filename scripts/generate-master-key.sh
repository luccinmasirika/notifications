#!/bin/bash

# Script to generate a secure AES-256 master key for encrypting API secrets
# Usage: ./scripts/generate-master-key.sh

echo "Generating AES-256 master key for API secret encryption..."
echo ""

# Generate 32 random bytes (256 bits) and encode in base64
if command -v openssl &> /dev/null; then
    MASTER_KEY=$(openssl rand -base64 32)
    echo "✅ Master key generated successfully!"
    echo ""
    echo "Add this to your environment variables:"
    echo "  export APP_CRYPTO_MASTER_KEY=\"$MASTER_KEY\""
    echo ""
    echo "Or add to docker-compose.yml:"
    echo "  APP_CRYPTO_MASTER_KEY: \"$MASTER_KEY\""
    echo ""
    echo "⚠️  IMPORTANT: Store this key securely. It cannot be recovered!"
    echo "⚠️  If you lose this key, all encrypted API secrets will be unrecoverable!"
    echo ""
    echo "For production, use a secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.)"
elif command -v python3 &> /dev/null; then
    MASTER_KEY=$(python3 -c "import secrets, base64; print(base64.b64encode(secrets.token_bytes(32)).decode('utf-8'))")
    echo "✅ Master key generated successfully!"
    echo ""
    echo "Add this to your environment variables:"
    echo "  export APP_CRYPTO_MASTER_KEY=\"$MASTER_KEY\""
    echo ""
    echo "Or add to docker-compose.yml:"
    echo "  APP_CRYPTO_MASTER_KEY: \"$MASTER_KEY\""
    echo ""
    echo "⚠️  IMPORTANT: Store this key securely. It cannot be recovered!"
    echo "⚠️  If you lose this key, all encrypted API secrets will be unrecoverable!"
    echo ""
    echo "For production, use a secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.)"
else
    echo "❌ Error: Neither openssl nor python3 found. Cannot generate master key."
    echo "Please install openssl or python3, or generate the key manually using Java:"
    echo ""
    echo "  java -cp backend/target/classes com.irembo.notifications.service.CryptoService"
    exit 1
fi

