#!/bin/sh

API_URL=${API_URL:-http://localhost:8080}

BASE_URL=$(echo "$API_URL" | sed 's|/admin$||' | sed 's|/api/notifications$||')

cat > /usr/share/nginx/html/assets/config.json <<EOF
{
  "apiUrl": "${BASE_URL}/admin",
  "notificationApiUrl": "${BASE_URL}/api/notifications"
}
EOF

echo "Configuration generated:"
echo "  - API URL: ${BASE_URL}/admin"
echo "  - Notification API URL: ${BASE_URL}/api/notifications"

exec nginx -g "daemon off;"
