# Deploy Guide — LocalWeb VPS

**Aplicação:** casamento-backend (Quarkus 3.36 / Java 21)  
**Data:** 2026-07-13

---

## ⚠️ Aviso sobre os planos LocalWeb

O site da LocalWeb (localweb.com.br/vps) não pôde ser acessado automaticamente.  
**Verifique os planos e preços atuais em:**  
👉 https://www.localweb.com.br/vps/servidores-vps/

---

## 1. Plano recomendado (critério técnico)

### Modo JVM (recomendado para produção inicial)

Com base nos requisitos levantados (Quarkus JVM + PostgreSQL + POI + uploads de 55 MB):

| Recurso | Mínimo | Recomendado |
|---|---|---|
| **vCPU** | 1 | **2** |
| **RAM** | 1 GB | **2 GB** |
| **Disco SSD** | 20 GB | **40 GB** |
| **OS** | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| **Acesso** | Root SSH | Root SSH |

> Procure o plano **VPS intermediário/médio** que ofereça 2 vCPU + 2 GB RAM.

### Modo Native (menor consumo de memória)

Se optar por build nativo (veja Seção 4):

| Recurso | Mínimo | Recomendado |
|---|---|---|
| **vCPU** | 1 | 2 |
| **RAM** | **512 MB** | **1 GB** |
| **Disco SSD** | 10 GB | 20 GB |

> Economia de ~60–70% de RAM. Ideal se quiser economizar no plano após os testes iniciais.

---

## 2. Pré-requisitos no servidor

```bash
# Atualizar o sistema
sudo apt update && sudo apt upgrade -y

# Instalar Docker (para modo JVM em container ou build nativo)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Instalar Docker Compose
sudo apt install -y docker-compose-plugin

# Instalar PostgreSQL 16 (se não usar container)
sudo apt install -y postgresql-16 postgresql-client-16

# Java 21 (apenas para modo JVM sem container)
sudo apt install -y openjdk-21-jre-headless
java -version  # deve mostrar OpenJDK 21

# Nginx (reverse proxy)
sudo apt install -y nginx
sudo systemctl enable nginx
```

---

## 3. Deploy Modo JVM (com Docker Compose)

Esta é a forma mais simples e robusta para a LocalWeb VPS.

### 3.1 Estrutura de arquivos no servidor

```
/opt/casamento/
├── docker-compose.yml
├── .env
└── keys/
    ├── privateKey.pem
    └── publicKey.pem
```

### 3.2 `docker-compose.yml` de produção

Criar em `/opt/casamento/docker-compose.yml`:

```yaml
version: "3.9"

services:

  db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: casamento
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: casamento-backend:latest
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://db:5432/casamento
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_ISSUER: ${JWT_ISSUER}
      APP_FRONTEND_URL: ${APP_FRONTEND_URL}
      APP_BASE_URL: ${APP_BASE_URL}
      R2_ENDPOINT: ${R2_ENDPOINT}
      R2_ACCESS_KEY: ${R2_ACCESS_KEY}
      R2_SECRET_KEY: ${R2_SECRET_KEY}
      R2_BUCKET: ${R2_BUCKET}
      R2_REGION: ${R2_REGION}
      R2_PUBLIC_BASE_URL: ${R2_PUBLIC_BASE_URL}
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS}
      JOB_SECRET: ${JOB_SECRET}
      JAVA_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC"
    volumes:
      - ./keys:/deployments/keys:ro
    ports:
      - "127.0.0.1:8080:8080"

volumes:
  pg_data:
```

### 3.3 Arquivo `.env`

Criar em `/opt/casamento/.env` (permissões 600):

```bash
chmod 600 /opt/casamento/.env
```

Conteúdo:
```env
DB_USERNAME=casamento
DB_PASSWORD=SenhaForteAqui123!
JWT_ISSUER=https://seudominio.com.br
APP_FRONTEND_URL=https://seudominio.com.br
APP_BASE_URL=https://api.seudominio.com.br
R2_ENDPOINT=https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=sua_access_key
R2_SECRET_KEY=sua_secret_key
R2_BUCKET=casamento-media-prod
R2_REGION=auto
R2_PUBLIC_BASE_URL=https://media.seudominio.com.br
CORS_ALLOWED_ORIGINS=https://seudominio.com.br
JOB_SECRET=segredo-jobs-prod-mudar
```

### 3.4 Build e push da imagem (executar na máquina de desenvolvimento)

```bash
# Build do JAR
./mvnw package -Pnative=false -DskipTests

# Build da imagem Docker JVM
docker build -f src/main/docker/Dockerfile.jvm \
  -t casamento-backend:latest .

# Salvar a imagem em arquivo para envio
docker save casamento-backend:latest | gzip > casamento-backend.tar.gz

# Enviar para o servidor
scp casamento-backend.tar.gz usuario@IP_DO_SERVIDOR:/opt/casamento/
```

### 3.5 Carregar e iniciar no servidor

```bash
# No servidor
cd /opt/casamento

# Carregar imagem
docker load < casamento-backend.tar.gz

# Iniciar
docker compose up -d

# Verificar logs
docker compose logs -f app
```

---

## 4. Deploy Modo Native (menor consumo de RAM)

> **Atenção:** O build nativo requer ~4–6 GB de RAM e 30+ minutos.  
> Deve ser feito na máquina de desenvolvimento (Linux x86_64), **não** no servidor VPS.

### 4.1 Pré-requisitos de build (máquina Linux de desenvolvimento)

```bash
# Instalar Docker (necessário para build nativo sem GraalVM local)
sudo apt install -y docker.io
```

### 4.2 Comando de build nativo via container (sem instalar GraalVM)

```bash
# Dentro do projeto (Linux x86_64)
./mvnw package -Pnative \
  -Dquarkus.native.container-build=true \
  -DskipTests

# O binário será gerado em:
# target/casamento-backend-1.0.0-SNAPSHOT-runner
```

> O Quarkus usa automaticamente a imagem `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-21` para compilar dentro de um container Docker — nenhum GraalVM precisa ser instalado localmente.

### 4.3 Limitação importante para este projeto

Antes de usar em produção, **teste as seguintes funcionalidades** no modo nativo:

| Funcionalidade | Risco no modo nativo |
|---|---|
| Apache POI (importação Excel) | ⚠️ Suporte parcial — testar com `./mvnw verify -Pnative` |
| iCal4j | ⚠️ Reflexão dinâmica — pode precisar de hints |
| ZXing (QR Code) | ✅ Funciona normalmente |
| OpenCSV | ✅ Funciona normalmente |
| AWS SDK v2 | ✅ Suportado com hints automáticos do Quarkus |

Se o POI falhar em modo nativo, adicionar em `src/main/resources/application.properties`:
```properties
quarkus.native.additional-build-args=--report-unsupported-elements-at-runtime
```

### 4.4 Build da imagem Docker nativa

```bash
# Após o build nativo
docker build -f src/main/docker/Dockerfile.native-micro \
  -t casamento-backend-native:latest .

# Salvar e enviar (< 100 MB vs ~400 MB do JVM)
docker save casamento-backend-native:latest | gzip > casamento-backend-native.tar.gz
scp casamento-backend-native.tar.gz usuario@IP_DO_SERVIDOR:/opt/casamento/
```

### 4.5 `docker-compose.yml` para modo nativo

Apenas mude a imagem no `docker-compose.yml`:
```yaml
  app:
    image: casamento-backend-native:latest  # ← trocar esta linha
    # ... resto igual
    environment:
      JAVA_OPTS: ""  # ← remover — executável nativo não usa JVM
```

---

## 5. Nginx como reverse proxy (HTTPS)

### 5.1 Instalar Certbot (SSL gratuito Let's Encrypt)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.seudominio.com.br
```

### 5.2 `/etc/nginx/sites-available/casamento`

```nginx
server {
    listen 443 ssl;
    server_name api.seudominio.com.br;

    ssl_certificate     /etc/letsencrypt/live/api.seudominio.com.br/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.seudominio.com.br/privkey.pem;

    # Tamanho máximo para upload de mídia (55 MB)
    client_max_body_size 60M;

    # Timeout para uploads de vídeo
    proxy_read_timeout 120s;
    proxy_send_timeout 120s;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name api.seudominio.com.br;
    return 301 https://$host$request_uri;
}
```

```bash
sudo ln -s /etc/nginx/sites-available/casamento /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## 6. Firewall

Abrir portas essenciais para o backend acessível via HTTPS público:

```bash
# UFW (Ubuntu Firewall)
sudo ufw allow 22    # SSH
sudo ufw allow 80    # HTTP (redirect)
sudo ufw allow 443   # HTTPS
sudo ufw enable

# Verificar
sudo ufw status
```

---

## 7. Variáveis de ambiente críticas para produção

```bash
# Gerar par de chaves JWT RSA (executar uma vez)
openssl genrsa -out privateKey.pem 2048
openssl rsa -in privateKey.pem -pubout -out publicKey.pem

# Copiar para o servidor
scp privateKey.pem publicKey.pem usuario@IP:/opt/casamento/keys/
```

No `.env`, ajustar:
```env
JWT_PRIVATE_KEY_LOCATION=/deployments/keys/privateKey.pem
JWT_PUBLIC_KEY_LOCATION=/deployments/keys/publicKey.pem
```

---

## 8. Monitoramento e saúde

```bash
# Health check da aplicação
curl https://api.seudominio.com.br/q/health

# Logs em tempo real
docker compose -f /opt/casamento/docker-compose.yml logs -f app

# Uso de recursos
docker stats
```

---

## 9. Script de deploy automático

Salvar em `/opt/casamento/deploy.sh`:

```bash
#!/bin/bash
set -e

IMAGE=$1  # ex: casamento-backend.tar.gz ou casamento-backend-native.tar.gz

echo "▶ Carregando imagem $IMAGE..."
docker load < "/opt/casamento/$IMAGE"

echo "▶ Reiniciando aplicação..."
docker compose -f /opt/casamento/docker-compose.yml up -d --no-deps --build app

echo "▶ Aguardando health check..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/q/health/live > /dev/null 2>&1; then
        echo "✅ Aplicação saudável!"
        exit 0
    fi
    sleep 2
done

echo "❌ Aplicação não respondeu após 60s"
docker compose -f /opt/casamento/docker-compose.yml logs --tail=50 app
exit 1
```

```bash
chmod +x /opt/casamento/deploy.sh

# Uso:
./deploy.sh casamento-backend.tar.gz       # JVM
./deploy.sh casamento-backend-native.tar.gz  # Native
```

---

## 10. Resumo de custos e plano recomendado

| Modo | RAM necessária | Plano sugerido | Observação |
|---|---|---|---|
| **JVM (recomendado)** | 2 GB | Plano médio LocalWeb VPS | Mais estável, mais fácil de debugar |
| **Native** | 512 MB – 1 GB | Plano básico LocalWeb VPS | Menor custo, requer testes antes |

> 💡 **Recomendação prática:** Inicie com **modo JVM + plano 2 GB** para o dia do casamento (estabilidade máxima). Após o evento, migre para **modo nativo + plano 1 GB** se quiser reduzir custo.
>
> Verifique os planos atuais em: **https://www.localweb.com.br/vps/servidores-vps/**
