# 📚 Index de la Documentation JMeter

Guide de navigation dans la documentation et les ressources JMeter pour l'API Irembo Notifications.

## 🚀 Par où commencer ?

### Pour les débutants

1. **[QUICKSTART.md](./QUICKSTART.md)** ⭐ **COMMENCEZ ICI**
   - Installation rapide (5 minutes)
   - Premiers tests en 3 étapes
   - Commandes essentielles
   - Problèmes courants

2. **[CHECKLIST.md](./CHECKLIST.md)**
   - Liste de vérification étape par étape
   - Validation de l'installation
   - Tests de base à exécuter
   - Critères de succès

### Pour les utilisateurs avancés

3. **[README.md](./README.md)**
   - Documentation complète et détaillée
   - Configuration avancée
   - Tous les scénarios de tests
   - Troubleshooting approfondi
   - Intégration CI/CD

4. **[SUMMARY.md](./SUMMARY.md)**
   - Vue d'ensemble du projet
   - Fonctionnalités principales
   - Configuration par défaut
   - Métriques importantes

## 📁 Fichiers de configuration

### Configuration principale

- **[jmeter.properties](./jmeter.properties)**
  - Configuration serveur (host, port, protocol)
  - Credentials (API key, secret, admin)
  - Paramètres de tests (threads, duration, rampup)
  - Limites de rate limiting

### Données de test

- **[test-data.csv](./test-data.csv)**
  - 20 scénarios de notifications
  - Canaux : SMS, EMAIL
  - Messages variés pour tests réalistes

### Scripts

- **[scripts/hmac-signature.groovy](./scripts/hmac-signature.groovy)**
  - Génération de signature HMAC-SHA256
  - Utilisé automatiquement dans les tests

## 🧪 Plans de tests JMeter (.jmx)

### 1. Test de charge (Load Test)

**[Notifications-Load-Test.jmx](./Notifications-Load-Test.jmx)** (21 KB)

**Objectif :** Test de performance avec authentification HMAC

**Caractéristiques :**
- Authentification HMAC automatique
- Données CSV variables
- Extraction headers rate limiting
- Support think time configurable
- Rapports HTML automatiques

**Usage :**
```bash
# CLI (recommandé)
./run-tests.sh load --threads=20 --duration=300

# GUI (développement)
jmeter -t Notifications-Load-Test.jmx
```

**Métriques mesurées :**
- Response Time (avg, 95th, 99th percentile)
- Throughput
- Error Rate
- Rate Limit Headers

### 2. Test de Rate Limiting

**[Rate-Limiting-Test.jmx](./Rate-Limiting-Test.jmx)** (30 KB)

**Objectif :** Valider le comportement de rate limiting (soft throttle et hard reject)

**Phases de test :**
1. Phase 1 : 80 requêtes (approche soft throttle)
2. Phase 2 : 19 requêtes (soft throttle actif)
3. Phase 3 : 5 requêtes (hard reject)

**Usage :**
```bash
# CLI
./run-tests.sh rate-limit

# GUI
jmeter -t Rate-Limiting-Test.jmx
```

**Validations :**
- ✅ 202 Accepted (0-79 requêtes)
- ✅ 202 + X-Soft-Throttled (80-99)
- ✅ 429 Too Many Requests (100+)

### 3. Test Admin API

**[Admin-API-Test.jmx](./Admin-API-Test.jmx)** (29 KB)

**Objectif :** Tests fonctionnels des endpoints d'administration

**Endpoints testés :**
1. GET `/admin/status`
2. GET `/admin/clients/page`
3. GET `/admin/generate-api-key`
4. POST `/admin/clients`
5. GET `/admin/clients/{id}/details`
6. PUT `/admin/clients/{id}/limits`
7. GET `/admin/system-limits`
8. DELETE `/admin/clients/{id}`

**Usage :**
```bash
# CLI
./run-tests.sh admin

# GUI
jmeter -t Admin-API-Test.jmx
```

**Authentification :** Basic Auth (admin/admin123)

## 🛠️ Scripts d'automatisation

### 1. Script d'exécution principal

**[run-tests.sh](./run-tests.sh)** (exécutable)

**Fonctionnalités :**
- Vérification automatique des prérequis
- Exécution de tous les types de tests
- Génération automatique de rapports HTML
- Configuration via CLI ou variables d'environnement
- Messages colorés et informatifs

**Commandes disponibles :**
```bash
./run-tests.sh load         # Test de charge
./run-tests.sh rate-limit   # Test de rate limiting
./run-tests.sh admin        # Test Admin API
./run-tests.sh all          # Tous les tests

# Options
--threads=N     # Nombre de threads
--duration=N    # Durée en secondes
--rampup=N      # Temps de montée
--host=HOST     # Serveur cible
--port=PORT     # Port
--protocol=P    # http ou https

# Aide
./run-tests.sh --help
```

### 2. Script de validation

**[validate-setup.sh](./validate-setup.sh)** (exécutable)

**Fonctionnalités :**
- Vérification présence de tous les fichiers
- Validation du format des fichiers
- Contrôle des permissions
- Vérification de la configuration

**Usage :**
```bash
./validate-setup.sh
```

## 📊 Résultats des tests

### Dossier results/

**[results/](./results/)** (gitignored)

**Contient :**
- Fichiers `.jtl` (JMeter Test Log)
- Rapports HTML générés
- Logs de tests

**Structure typique :**
```
results/
├── load-test-20251207-153045.jtl
├── html-report-load-20251207-153045/
│   ├── index.html
│   ├── statistics.json
│   └── ...
├── rate-limit-test-20251207-154000.jtl
└── ...
```

**Nettoyage :**
```bash
# Supprimer les résultats de plus de 7 jours
find results/ -mtime +7 -delete
```

## 📖 Documentation par cas d'usage

### Je veux tester rapidement l'API

1. Lire [QUICKSTART.md](./QUICKSTART.md) (5 min)
2. Exécuter :
   ```bash
   docker-compose up -d
   cd jmeter
   ./run-tests.sh load --threads=5 --duration=60
   ```

### Je veux valider le rate limiting

1. Consulter section "Rate Limiting" dans [README.md](./README.md)
2. Exécuter :
   ```bash
   ./run-tests.sh rate-limit
   curl http://localhost:1310/actuator/metrics/ratelimiter.hard_reject
   ```

### Je veux créer mes propres tests

1. Lire [README.md](./README.md) section "Personnalisation"
2. Ouvrir JMeter GUI :
   ```bash
   jmeter -t Notifications-Load-Test.jmx
   ```
3. Modifier et sauvegarder

### Je veux intégrer dans CI/CD

1. Consulter [README.md](./README.md) section "Intégration CI/CD"
2. Utiliser le script `run-tests.sh` dans votre pipeline
3. Archiver les résultats (dossier `results/`)

### Je veux comprendre les résultats

1. Ouvrir le rapport HTML généré
2. Consulter [README.md](./README.md) section "Analyse des résultats"
3. Vérifier les métriques clés dans [SUMMARY.md](./SUMMARY.md)

## 🎯 Scénarios de tests recommandés

### Scénario 1 : Validation quotidienne (10 min)

```bash
# 1. Test Admin API
./run-tests.sh admin

# 2. Test de charge léger
./run-tests.sh load --threads=10 --duration=180

# 3. Vérifier métriques
curl http://localhost:1310/actuator/metrics
```

### Scénario 2 : Test de régression (30 min)

```bash
# 1. Tous les tests
./run-tests.sh all

# 2. Analyser les rapports HTML
open results/html-report-*/index.html
```

### Scénario 3 : Test de capacité (1 heure)

```bash
# Warm-up
./run-tests.sh load --threads=10 --duration=300

# Load normal
./run-tests.sh load --threads=25 --duration=600

# Stress test
./run-tests.sh load --threads=50 --duration=300

# Peak test
./run-tests.sh load --threads=100 --duration=300
```

## 🔗 Liens rapides

### Documentation interne

- 📄 [README.md](./README.md) - Documentation complète
- 🚀 [QUICKSTART.md](./QUICKSTART.md) - Démarrage rapide
- 📋 [SUMMARY.md](./SUMMARY.md) - Résumé du projet
- ✅ [CHECKLIST.md](./CHECKLIST.md) - Liste de vérification
- 📚 [INDEX.md](./INDEX.md) - Ce fichier

### Configuration

- ⚙️ [jmeter.properties](./jmeter.properties)
- 📊 [test-data.csv](./test-data.csv)
- 🔐 [scripts/hmac-signature.groovy](./scripts/hmac-signature.groovy)

### Plans de tests

- 🧪 [Notifications-Load-Test.jmx](./Notifications-Load-Test.jmx)
- 🚦 [Rate-Limiting-Test.jmx](./Rate-Limiting-Test.jmx)
- 👤 [Admin-API-Test.jmx](./Admin-API-Test.jmx)

### Scripts

- 🚀 [run-tests.sh](./run-tests.sh)
- ✅ [validate-setup.sh](./validate-setup.sh)

### Documentation externe

- [API Swagger UI](http://localhost:1310/swagger-ui.html)
- [JMeter Documentation](https://jmeter.apache.org/usermanual/index.html)
- [Postman Collection](../Irembo-Notifications-API.postman_collection.json)

## 📞 Support

### Problèmes communs

| Problème | Fichier de référence | Section |
|----------|---------------------|---------|
| Installation | [QUICKSTART.md](./QUICKSTART.md) | Étape 1 |
| Configuration | [README.md](./README.md) | Configuration |
| Erreur 401 | [README.md](./README.md) | Troubleshooting |
| Rate limiting | [QUICKSTART.md](./QUICKSTART.md) | Scénario 2 |
| Performance | [README.md](./README.md) | Analyse des résultats |

### Logs et debug

```bash
# Logs application
docker-compose logs -f backend

# Logs JMeter (fichiers .jtl dans results/)
tail -f results/load-test-*.jtl

# Validation setup
./validate-setup.sh

# Aide script
./run-tests.sh --help
```

## 🎓 Parcours d'apprentissage suggéré

### Niveau 1 : Débutant (1-2 heures)

1. ✅ Lire [QUICKSTART.md](./QUICKSTART.md)
2. ✅ Suivre [CHECKLIST.md](./CHECKLIST.md) jusqu'à "Tests de base"
3. ✅ Exécuter le premier test avec `run-tests.sh`
4. ✅ Ouvrir un rapport HTML

### Niveau 2 : Intermédiaire (3-4 heures)

1. ✅ Lire [README.md](./README.md) en entier
2. ✅ Comprendre l'authentification HMAC
3. ✅ Exécuter tous les types de tests
4. ✅ Ouvrir et comprendre un plan .jmx dans JMeter GUI
5. ✅ Modifier un test simple

### Niveau 3 : Avancé (1 journée)

1. ✅ Créer un plan de test personnalisé
2. ✅ Comprendre tous les paramètres de configuration
3. ✅ Intégrer dans un pipeline CI/CD
4. ✅ Configurer des seuils de performance
5. ✅ Analyser des métriques Prometheus/Grafana

## 📊 Statistiques du projet

- **11 fichiers principaux** (JMX, MD, scripts)
- **3 plans de tests JMeter** (80 KB total)
- **3200+ lignes de code/documentation**
- **20 scénarios de test** dans CSV
- **8 endpoints Admin** testés
- **3 phases de rate limiting** validées
- **Support HMAC-SHA256** complet

## 🎉 Conclusion

Cette configuration JMeter est **production-ready** et couvre :
- ✅ Tests de charge et performance
- ✅ Validation du rate limiting
- ✅ Tests fonctionnels Admin API
- ✅ Authentification HMAC complète
- ✅ Rapports HTML détaillés
- ✅ Intégration CI/CD

**Commencez maintenant :**
```bash
cd /Users/luccinmasirika/Developer/irembo/notifications/jmeter
cat QUICKSTART.md  # Lire le guide rapide
./run-tests.sh load  # Lancer votre premier test
```

---

**Dernière mise à jour** : December 2025  
**Version** : 1.0.0  
**Auteur** : Irembo Engineering Team

