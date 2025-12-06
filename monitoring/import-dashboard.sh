#!/bin/bash

GRAFANA_URL="http://localhost:3000"
GRAFANA_USER="admin"
GRAFANA_PASSWORD="admin123"
DASHBOARD_FILE="./monitoring/grafana/dashboard-files/notifications-dashboard.json"

echo "Importation du dashboard Grafana..."

if ! curl -s -f -u "$GRAFANA_USER:$GRAFANA_PASSWORD" "$GRAFANA_URL/api/health" > /dev/null; then
  echo "❌ Erreur: Grafana n'est pas accessible sur $GRAFANA_URL"
  echo "Assurez-vous que Grafana est démarré: docker-compose ps grafana"
  exit 1
fi

echo "✅ Grafana est accessible"

TEMP_FILE=$(mktemp)
echo '{"dashboard":' > $TEMP_FILE
cat $DASHBOARD_FILE >> $TEMP_FILE
echo ',"overwrite":true}' >> $TEMP_FILE

RESPONSE=$(curl -s -X POST \
  -u "$GRAFANA_USER:$GRAFANA_PASSWORD" \
  -H "Content-Type: application/json" \
  -d @$TEMP_FILE \
  $GRAFANA_URL/api/dashboards/db)

rm $TEMP_FILE

SUCCESS=$(echo $RESPONSE | jq -r '.status')

if [ "$SUCCESS" == "success" ]; then
  DASHBOARD_URL=$(echo $RESPONSE | jq -r '.url')
  echo "✅ Dashboard importé avec succès!"
  echo "📊 Accédez au dashboard: $GRAFANA_URL$DASHBOARD_URL"
else
  ERROR=$(echo $RESPONSE | jq -r '.message // .error')
  echo "⚠️  Dashboard non importé: $ERROR"
  echo ""
  echo "💡 Essayez d'importer manuellement:"
  echo "   1. Ouvrez $GRAFANA_URL"
  echo "   2. Menu '+' → Import dashboard"
  echo "   3. Collez le contenu de $DASHBOARD_FILE"
fi
