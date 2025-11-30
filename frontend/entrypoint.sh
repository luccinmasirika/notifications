#!/bin/sh

# Set default API URL if not provided
API_URL=${API_URL:-http://localhost:8080}

# Extract base URL without /admin or /api/notifications
BASE_URL=$(echo "$API_URL" | sed 's|/admin$||' | sed 's|/api/notifications$||')

# Generate config.json from template
cat > /usr/share/nginx/html/assets/config.json <<EOF
{
  "apiUrl": "${BASE_URL}/admin",
  "notificationApiUrl": "${BASE_URL}/api/notifications"
}
EOF

echo "Configuration generated:"
echo "  - API URL: ${BASE_URL}/admin"
echo "  - Notification API URL: ${BASE_URL}/api/notifications"

# Start nginx
exec nginx -g "daemon off;"
