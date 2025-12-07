# 📊 JMeter Configuration - Résumé

## ✅ Configuration complète installée

La configuration JMeter pour l'API Irembo Notifications est maintenant complète et prête à l'emploi !

### 📦 Fichiers créés

```
jmeter/
├── 📄 README.md                       - Documentation complète (en français)
├── 📄 QUICKSTART.md                   - Guide de démarrage rapide
├── 📄 SUMMARY.md                      - Ce fichier
├── ⚙️  jmeter.properties              - Configuration globale
├── 📊 test-data.csv                   - Données de test (20 scénarios)
│
├── 🧪 Plans de tests JMX
│   ├── Notifications-Load-Test.jmx    - Test de charge avec HMAC
│   ├── Rate-Limiting-Test.jmx         - Test de rate limiting
│   └── Admin-API-Test.jmx             - Test Admin API avec Basic Auth
│
├── 🔧 Scripts
│   └── scripts/hmac-signature.groovy  - Génération signature HMAC-SHA256
│
├── 🚀 run-tests.sh                    - Script d'exécution automatisé
│
└── 📁 results/                        - Résultats des tests (gitignored)
    └── .gitkeep
```

## 🎯 Fonctionnalités

### ✨ Points forts

1. **Authentification HMAC complète**
   - Génération automatique de signature HMAC-SHA256
   - Headers `X-API-Key`, `X-Timestamp`, `X-Signature`
   - Payload construit selon la spécification de l'API

2. **Tests de Rate Limiting avancés**
   - Test du soft throttle (80% du quota)
   - Test du hard reject (100% du quota)
   - Validation des headers de rate limiting
   - Vérification du comportement de throttling

3. **Tests Admin API complets**
   - CRUD des clients
   - Gestion des limites
   - Génération d'API keys
   - Rotation de secrets
   - Basic Authentication

4. **Configuration flexible**
   - Fichier de propriétés pour configuration centralisée
   - Variables d'environnement supportées
   - Données CSV pour variété des tests
   - Paramètres CLI pour personnalisation

5. **Reporting avancé**
   - Rapports HTML automatiques
   - Export JTL pour analyse externe
   - Support InfluxDB/Grafana (optionnel)
   - Logs détaillés avec assertions

## 🚀 Démarrage ultra-rapide

```bash
# 1. Démarrer l'application
cd /Users/luccinmasirika/Developer/irembo/notifications
docker-compose up -d

# 2. Attendre 30 secondes
sleep 30

# 3. Aller dans le dossier JMeter
cd jmeter

# 4. Lancer un test
./run-tests.sh load

# 🎉 C'est tout !
```

## 📋 Commandes essentielles

### Tests principaux

```bash
# Test de charge basique
./run-tests.sh load

# Test de charge intensif
./run-tests.sh load --threads=50 --duration=300

# Test de rate limiting
./run-tests.sh rate-limit

# Test Admin API
./run-tests.sh admin

# Tous les tests
./run-tests.sh all
```

### Mode GUI (développement)

```bash
# Ouvrir dans JMeter pour modification
jmeter -t Notifications-Load-Test.jmx
jmeter -t Rate-Limiting-Test.jmx
jmeter -t Admin-API-Test.jmx
```

### Mode CLI (production)

```bash
# Test avec génération de rapport HTML
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=20 \
  -Jtest.duration=180 \
  -l results/test-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o results/html-report
```

## 🎓 Scénarios de tests

### Scénario 1 : Validation fonctionnelle rapide

```bash
# Test Admin API (2 minutes)
./run-tests.sh admin

# Test de santé de l'API (1 minute)
./run-tests.sh load --threads=5 --duration=60
```

### Scénario 2 : Test de charge progressif

```bash
# Warm-up (5 min, 10 users)
./run-tests.sh load --threads=10 --duration=300

# Load normal (10 min, 25 users)
./run-tests.sh load --threads=25 --duration=600

# Stress test (5 min, 50 users)
./run-tests.sh load --threads=50 --duration=300
```

### Scénario 3 : Validation complète du rate limiting

```bash
# Test du comportement de rate limiting
./run-tests.sh rate-limit

# Vérifier les métriques
curl http://localhost:1310/actuator/metrics/ratelimiter.soft_throttle
curl http://localhost:1310/actuator/metrics/ratelimiter.hard_reject

# Attendre reset (60 secondes)
sleep 60

# Re-tester pour confirmer le reset
./run-tests.sh rate-limit
```

## 📊 Configuration par défaut

### Serveur

| Paramètre | Valeur par défaut | Description |
|-----------|-------------------|-------------|
| `server.host` | `localhost` | Hôte du serveur |
| `server.port` | `1310` | Port du serveur |
| `server.protocol` | `http` | Protocole (http/https) |

### Authentification

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| `api.key` | `LMirg1mq_...` | API Key du client TestClient |
| `api.secret` | `W0yVOb0m...` | API Secret pour HMAC |
| `admin.username` | `admin` | Username Admin |
| `admin.password` | `admin123` | Password Admin |

### Tests

| Paramètre | Valeur par défaut | Description |
|-----------|-------------------|-------------|
| `threads.count` | `10` | Nombre d'utilisateurs virtuels |
| `rampup.time` | `5` | Temps de montée en charge (s) |
| `test.duration` | `60` | Durée du test (s) |
| `think.time.min` | `500` | Think time minimum (ms) |
| `think.time.max` | `2000` | Think time maximum (ms) |

### Rate Limiting

| Paramètre | Valeur | Description |
|-----------|--------|-------------|
| `rate.limit.max.requests` | `100` | Limite par fenêtre |
| `rate.limit.window` | `60` | Fenêtre en secondes |
| `rate.limit.soft.threshold` | `80` | Seuil soft throttle (%) |
| `rate.limit.hard.threshold` | `100` | Seuil hard reject (%) |

## 🔍 Validation de la configuration

### Test rapide

```bash
# 1. Vérifier JMeter
jmeter --version

# 2. Vérifier le serveur
curl http://localhost:1310/health

# 3. Lancer un test court
./run-tests.sh load --threads=3 --duration=30

# 4. Vérifier les résultats
ls -lh results/
```

### Checklist de démarrage

- ✅ JMeter installé (5.6+)
- ✅ Java installé (11+)
- ✅ Docker Compose démarré
- ✅ Application en cours d'exécution (port 1310)
- ✅ Scripts exécutables (`chmod +x run-tests.sh`)
- ✅ Dossier results/ créé

## 🎯 Métriques importantes

### Performance

| Métrique | Objectif | Seuil d'alerte |
|----------|----------|----------------|
| **Avg Response Time** | < 200ms | > 500ms |
| **95th Percentile** | < 500ms | > 1000ms |
| **99th Percentile** | < 1000ms | > 2000ms |
| **Throughput** | Stable | Décroissant |
| **Error Rate** | 0% | > 1% |

### Rate Limiting

| Comportement | Requête | Code HTTP | Headers |
|--------------|---------|-----------|---------|
| **Normal** | 1-79 | 202 | `X-RateLimit-Remaining` |
| **Soft Throttle** | 80-99 | 202 | `X-Soft-Throttled: true` |
| **Hard Reject** | 100+ | 429 | `Retry-After: 30` |

## 📚 Documentation

### Fichiers de documentation

- **README.md** - Documentation détaillée complète
- **QUICKSTART.md** - Guide de démarrage rapide
- **SUMMARY.md** - Ce fichier récapitulatif

### Documentation externe

- [JMeter User Manual](https://jmeter.apache.org/usermanual/index.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
- [Swagger UI Local](http://localhost:1310/swagger-ui.html)

### Exemples dans le projet

- **Postman Collection** : `../Irembo-Notifications-API.postman_collection.json`
- **Tests d'intégration** : `../backend/src/test/java/com/irembo/notifications/integration/`
- **Seed Data** : `../backend/src/main/resources/db/migration/V4__seed_test_data.sql`

## 🐛 Troubleshooting

### Problèmes courants

| Problème | Cause | Solution |
|----------|-------|----------|
| Connection refused | App non démarrée | `docker-compose up -d` |
| 401 Unauthorized | HMAC incorrect | Vérifier `jmeter.properties` |
| 429 immédiat | Rate limit atteint | Attendre 60s |
| OutOfMemory | Trop de threads | Réduire threads ou augmenter heap |

### Commandes de debug

```bash
# Logs de l'application
docker-compose logs -f backend

# État des services
docker-compose ps

# Métriques système
curl http://localhost:1310/actuator/metrics

# Santé détaillée
curl http://localhost:1310/actuator/health | jq

# Rate limiting status
curl http://localhost:1310/actuator/metrics/ratelimiter.hard_reject
curl http://localhost:1310/actuator/metrics/ratelimiter.soft_throttle
```

## 🎓 Prochaines étapes

### 1. Personnalisation

- Modifier `jmeter.properties` pour vos besoins
- Ajouter des données dans `test-data.csv`
- Créer de nouveaux plans de tests
- Ajuster les assertions

### 2. Intégration CI/CD

```yaml
# Exemple GitHub Actions / GitLab CI
test:
  script:
    - cd jmeter
    - ./run-tests.sh load --threads=20 --duration=180
  artifacts:
    paths:
      - jmeter/results/
```

### 3. Monitoring avancé

- Configurer InfluxDB pour métriques temps réel
- Créer des dashboards Grafana personnalisés
- Alerting sur seuils de performance
- Intégration avec Prometheus

### 4. Tests avancés

- Tests de charge distribués (multi-nœuds)
- Tests de soak (longue durée)
- Tests de spike (pics soudains)
- Tests de concurrency

## 📝 Notes importantes

### Authentification HMAC

La signature HMAC est calculée comme suit :
```
payload = timestamp + "\n" + HTTP_METHOD + "\n" + PATH + "\n" + BODY
signature = Base64(HMAC-SHA256(api_secret, payload))
```

### Limites par défaut (TestClient)

- **Requêtes par fenêtre** : 100 / 60 secondes
- **Soft throttle** : 80 requêtes (80%)
- **Hard reject** : 100 requêtes (100%)
- **Quota mensuel** : 10,000 requêtes

### Credentials de test

Les credentials dans `jmeter.properties` correspondent au client TestClient créé par la migration `V4__seed_test_data.sql`.

⚠️ **Ne pas utiliser en production !**

## 🎉 Conclusion

Votre configuration JMeter est complète et prête à l'emploi pour :

- ✅ Tests de performance et de charge
- ✅ Validation du rate limiting
- ✅ Tests fonctionnels Admin API
- ✅ Tests d'authentification HMAC
- ✅ Génération de rapports détaillés
- ✅ Intégration CI/CD

### Démarrage immédiat

```bash
cd /Users/luccinmasirika/Developer/irembo/notifications/jmeter
./run-tests.sh load
```

### Pour plus d'informations

- Lire [QUICKSTART.md](./QUICKSTART.md) pour commencer rapidement
- Lire [README.md](./README.md) pour la documentation complète
- Exécuter `./run-tests.sh --help` pour l'aide du script

---

**Créé le** : December 2025  
**Version** : 1.0.0  
**Auteur** : Irembo Engineering Team

**Happy Testing! 🚀**
