MVN = /opt/maven/bin/mvn
QUARKUS_PROFILE ?= dev

.PHONY: help dev-db stop-db dev-backend test test-db build clean

help: ## Mostra esta ajuda
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

dev-db: ## Inicia PostgreSQL local (dev + test)
	docker compose up -d postgres postgres_test
	@echo "Aguardando PostgreSQL..."
	@until docker exec casamento_db pg_isready -U casamento -q; do sleep 1; done
	@echo "PostgreSQL pronto em localhost:5432"

stop-db: ## Para os containers de banco
	docker compose down

dev-backend: ## Inicia Quarkus em modo dev (hot reload)
	$(MVN) quarkus:dev -Dquarkus.profile=dev

test: ## Executa todos os testes
	$(MVN) test -Dquarkus.profile=test

test-db: ## Inicia apenas o banco de testes
	docker compose up -d postgres_test

build: ## Builda o jar de produção (fast-jar)
	$(MVN) package -Dquarkus.package.jar.type=fast-jar -DskipTests

clean: ## Limpa artefatos de build
	$(MVN) clean

generate-keys: ## Gera par de chaves RSA para JWT (desenvolvimento)
	@mkdir -p src/main/resources/META-INF/resources
	@openssl genrsa -out /tmp/private.pem 2048 2>/dev/null
	@openssl pkcs8 -topk8 -inform PEM -in /tmp/private.pem -out src/main/resources/META-INF/resources/privateKey.pem -nocrypt
	@openssl rsa -in /tmp/private.pem -pubout -out src/main/resources/META-INF/resources/publicKey.pem 2>/dev/null
	@rm /tmp/private.pem
	@echo "Chaves RSA geradas em src/main/resources/META-INF/resources/"
