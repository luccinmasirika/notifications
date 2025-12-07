#!/bin/bash

# Script simple pour démontrer la scalabilité
# Usage: ./scale-backends.sh <number>

NUM_BACKENDS=${1:-2}

echo "════════════════════════════════════════════════════════"
echo "  Démonstration de Scalabilité - Notifications API"
echo "════════════════════════════════════════════════════════"
echo ""
echo "Nombre de backends demandés: $NUM_BACKENDS"
echo ""

# Vérifier que docker compose est disponible
if ! command -v docker &> /dev/null; then
    echo "❌ Docker n'est pas installé"
    exit 1
fi

# Vérifier les backends actifs
echo "📊 Backends actifs actuellement:"
docker compose ps --format "table {{.Name}}\t{{.Status}}" | grep backend || echo "Aucun backend démarré"
echo ""

# Vérifier dans Traefik
echo "🔍 Vérification dans Traefik:"
SERVICES=$(curl -s http://localhost:8080/api/http/services 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "$SERVICES" | jq -r 'to_entries[] | select(.key | contains("backend")) | "  - \(.key): \(.value.loadBalancer.servers | length) serveur(s)"' 2>/dev/null || echo "  Impossible de parser les services"
else
    echo "  ⚠️  Traefik n'est pas accessible (http://localhost:8080)"
fi
echo ""

# Test de load balancing
echo "🧪 Test de Load Balancing (10 requêtes):"
echo ""
for i in {1..10}; do
    RESPONSE=$(curl -s http://localhost:1310/actuator/health 2>/dev/null)
    if [ $? -eq 0 ]; then
        STATUS=$(echo "$RESPONSE" | jq -r '.status' 2>/dev/null || echo "unknown")
        echo "  Requête $i: ✅ $STATUS"
    else
        echo "  Requête $i: ❌ Erreur"
    fi
    sleep 0.3
done
echo ""

echo "════════════════════════════════════════════════════════"
echo "💡 Pour ajouter des backends, ajoutez backend-3, backend-4, etc."
echo "   dans docker-compose.yml avec la même configuration que backend-2"
echo "════════════════════════════════════════════════════════"