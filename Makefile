.PHONY: down down-v up build up-detached build-front build-back help

help:
	@echo "Available commands:"
	@echo "  make down          - Stop and remove containers"
	@echo "  make down-v        - Stop and remove containers, networks, and volumes"
	@echo "  make up            - Build and start containers in foreground"
	@echo "  make build         - Build and start containers in foreground (alias for up)"
	@echo "  make up-detached   - Build and start containers in detached mode"
	@echo "  make build-front   - Build only the frontend service"
	@echo "  make build-back    - Build only the backend service"

down:
	docker compose down

down-v:
	docker compose down -v

up:
	docker compose up --build

build: up

up-detached:
	docker compose up --build -d

build-front:
	docker compose build frontend

build-back:
	docker compose build backend
