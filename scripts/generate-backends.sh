#!/bin/bash

# Script pour générer automatiquement des backends supplémentaires
# Usage: ./scripts/generate-backends.sh <nombre_total>
# Exemple: ./scripts/generate-backends.sh 5 (génère backend-3, backend-4, backend-5)

NUM_BACKENDS=${1:-3}
OUTPUT_FILE="docker-compose.generated.yml"

if [ "$NUM_BACKENDS" -le 2 ]; then
    echo "❌ Le nombre doit être > 2 (backend-1 et backend-2 existent déjà)"
    echo "   Usage: ./scripts/generate-backends.sh <nombre_total>"
    echo "   Exemple: ./scripts/generate-backends.sh 5"
    exit 1
fi

echo "🔧 Génération de backends 3 à $NUM_BACKENDS..."

cat > $OUTPUT_FILE << 'EOF'
# ═══════════════════════════════════════════════════════════════════
# Fichier généré automatiquement - NE PAS MODIFIER MANUELLEMENT
# ═══════════════════════════════════════════════════════════════════
version: '3.8'

services:
EOF

for i in $(seq 3 $NUM_BACKENDS); do
  cat >> $OUTPUT_FILE << EOF

  backend-$i:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: notifications-backend-$i
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/notifications
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: guest
      SPRING_RABBITMQ_PASSWORD: guest
      ADMIN_USERNAME: admin
      ADMIN_PASSWORD: admin123
      CORS_ALLOWED_ORIGINS: "http://localhost,http://localhost:1015,http://localhost:4200,http://localhost:8080"
      APP_CRYPTO_MASTER_KEY: \${APP_CRYPTO_MASTER_KEY:-uo0mWscOs31YO4l7nxkswP6bvKghQr01jTyL+VrwwjA=}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:1310/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.backend.rule=PathPrefix(\`/api\`) || PathPrefix(\`/admin\`) || PathPrefix(\`/actuator\`) || PathPrefix(\`/swagger-ui\`) || PathPrefix(\`/v3/api-docs\`)"
      - "traefik.http.routers.backend.entrypoints=web"
      - "traefik.http.services.backend-service.loadbalancer.server.port=1310"
      - "traefik.http.services.backend-service.loadbalancer.healthcheck.path=/actuator/health"
      - "traefik.http.services.backend-service.loadbalancer.healthcheck.interval=10s"
      - "traefik.http.services.backend-service.loadbalancer.healthcheck.timeout=3s"
      - "traefik.http.services.backend-service.loadbalancer.healthcheck.scheme=http"
      - "traefik.http.routers.backend.middlewares=cors@docker,security-headers@docker,compress@docker,retry@docker,circuit-breaker@docker"
    networks:
      - notifications-network
EOF
done

echo "✅ Fichier généré : $OUTPUT_FILE"
echo ""
echo "📋 Pour utiliser :"
echo "   docker compose -f docker-compose.yml -f $OUTPUT_FILE up -d"
echo ""
echo "📊 Backends générés : backend-3 à backend-$NUM_BACKENDS"
