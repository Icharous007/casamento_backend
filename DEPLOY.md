# Manual de deploy na Locaweb

Este guia publica o frontend React/Vite e este backend Quarkus na mesma VPS Locaweb, com HTTPS gratuito.

| Item | Endereco final |
|---|---|
| Site | `https://gustavoemalucasam.net.br` |
| API | `https://gustavoemalucasam.net.br/api/v1` |
| Saude interna | `http://127.0.0.1:8080/q/health/live` |

O Nginx recebe trafego publico nas portas 80/443, serve o frontend e encaminha `/api` ao Quarkus. Backend e PostgreSQL ficam privados em Docker; o frontend ja usa `/api/v1`, portanto nao ha CORS entre navegador e API em producao.

## 1. Antes de contratar

Contrate uma VPS Linux com Ubuntu LTS, IP publico, 2 vCPU, 4 GB RAM e 40 GB SSD. O minimo de 2 GB pode funcionar, mas nao e recomendado sem teste do pico de uploads e compressao de video. Escolha Ubuntu 24.04 LTS quando disponivel; 22.04 LTS tambem funciona.

Voce precisa de acesso a Locaweb, ao DNS de `gustavoemalucasam.net.br`, aos repositorios backend e frontend e a uma conta Cloudflare R2 configurada conforme [R2_SETUP.md](R2_SETUP.md). Ative autenticacao de dois fatores na conta Locaweb.

Na maquina de desenvolvimento Linux, instale Git, Docker Engine, Java 21, Node.js 22 e pnpm. Na VPS nao e necessario Java ou Node: ela recebera a imagem Docker e o build estatico.

## 2. Criar VPS e acesso SSH

1. Na Central do Cliente Locaweb, abra **Produtos** -> **Cloud Server/VPS** e crie/contrate uma VPS Linux com a configuracao acima.
2. Anote IP publico, usuario e senha inicial. Confirme no painel que nao ha bloqueio adicional para portas 80/443.
3. Na maquina local, crie a chave SSH e cadastre o conteudo de `~/.ssh/id_ed25519.pub` no painel, ou copie-a uma unica vez com a senha inicial:

```bash
ssh-keygen -t ed25519 -C "casamento-locaweb"
ssh-copy-id root@IP_DA_VPS
ssh root@IP_DA_VPS
```

4. Na VPS, crie uma conta administrativa. Troque `SEU_USUARIO` pelo seu login:

```bash
apt update && apt upgrade -y
adduser SEU_USUARIO
usermod -aG sudo SEU_USUARIO
mkdir -p /home/SEU_USUARIO/.ssh
cp /root/.ssh/authorized_keys /home/SEU_USUARIO/.ssh/
chown -R SEU_USUARIO:SEU_USUARIO /home/SEU_USUARIO/.ssh
chmod 700 /home/SEU_USUARIO/.ssh
chmod 600 /home/SEU_USUARIO/.ssh/authorized_keys
```

5. Em um segundo terminal, confirme `ssh SEU_USUARIO@IP_DA_VPS` antes de fechar a sessao root. Na Locaweb, gere um snapshot antes do primeiro deploy e antes de atualizacoes de risco. Snapshot nao substitui backup externo.

## 3. Apontar DNS

No provedor que controla os nameservers do dominio, crie este registro:

| Tipo | Nome | Valor |
|---|---|---|
| `A` | `@` | `IP_DA_VPS` |

Se o DNS estiver na Locaweb: Painel de Hospedagem -> dominio -> menu de tres pontos -> **Zona de DNS** -> **Adicionar entrada**. Aguarde a propagacao, que pode levar 4 a 24 horas:

```bash
dig +short gustavoemalucasam.net.br A
```

O resultado precisa ser o IP da VPS antes de emitir o certificado.

## 4. Instalar dependencias da VPS

Entre com a conta administrativa e execute:

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2 nginx certbot python3-certbot-nginx ufw rclone curl
sudo systemctl enable --now docker nginx
sudo usermod -aG docker "$USER"
```

Saia e entre novamente. Valide com `docker --version`, `docker compose version`, `nginx -v` e `certbot --version`.

Configure o firewall apenas depois de testar o SSH por chave:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status verbose
```

Nunca abra as portas 5432 ou 8080 ao publico.

## 5. Preparar arquivos e segredos

No repositorio backend local, envie os modelos de deploy:

```bash
rsync -av deploy/ SEU_USUARIO@IP_DA_VPS:/tmp/casamento-deploy/
```

Na VPS:

```bash
sudo mkdir -p /opt/casamento/{keys,releases,backups}
sudo cp /tmp/casamento-deploy/docker-compose.prod.yml /opt/casamento/
sudo install -m 750 /tmp/casamento-deploy/deploy-backend.sh /opt/casamento/
sudo install -m 750 /tmp/casamento-deploy/backup-postgres.sh /opt/casamento/
sudo chown -R "$USER":"$USER" /opt/casamento
cp /tmp/casamento-deploy/.env.production.example /opt/casamento/.env
chmod 600 /opt/casamento/.env
```

Edite `/opt/casamento/.env` com `nano`; substitua todos os valores `REPLACE_WITH_...`. Gere segredos com `openssl rand -base64 32`. O arquivo nao pode ir para o Git.

Mantenha estas URLs exatamente assim:

```env
JWT_ISSUER=https://gustavoemalucasam.net.br
APP_BASE_URL=https://gustavoemalucasam.net.br
APP_FRONTEND_URL=https://gustavoemalucasam.net.br
CORS_ALLOWED_ORIGINS=https://gustavoemalucasam.net.br
```

Gere as chaves JWT uma vez na VPS:

```bash
cd /opt/casamento/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out privateKey.pem
openssl rsa -pubout -in privateKey.pem -out publicKey.pem
chmod 600 privateKey.pem
chmod 644 publicKey.pem
```

Antes de divulgar o QR/link, ative explicitamente o evento. O perfil de produção bloqueia novos cadastros em eventos `DRAFT`:

```bash
docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml \
	exec -T db psql -U "$DB_USERNAME" -d "${DB_NAME:-casamento}" \
	-c "UPDATE events SET status = 'ACTIVE' WHERE slug = 'casamento-2027';"
```

Configure também o Worker privado e o CORS de upload direto descritos em [R2_SETUP.md](R2_SETUP.md). Em produção, `R2_PUBLIC_BASE_URL` deve ficar vazio e `MEDIA_DELIVERY_BASE_URL`/`MEDIA_DELIVERY_SIGNING_KEY` são obrigatórios.

## 6. Criar e publicar imagem Docker do backend

Execute na maquina de desenvolvimento, dentro de `casamento_backend`, nunca na VPS. O modo JVM e o recomendado inicialmente.

```bash
./mvnw -q -DskipTests clean package -Djavacpp.platform=linux-x86_64
VERSION=$(date -u +%Y%m%dT%H%M%SZ)
docker build -f src/main/docker/Dockerfile.jvm -t "casamento-backend:${VERSION}" .
docker image inspect "casamento-backend:${VERSION}" --format '{{.Id}}'
docker save "casamento-backend:${VERSION}" | gzip > "casamento-backend-${VERSION}.tar.gz"
sha256sum "casamento-backend-${VERSION}.tar.gz" > "casamento-backend-${VERSION}.tar.gz.sha256"
```

O parametro `-Djavacpp.platform=linux-x86_64` inclui somente binarios ffmpeg Linux. O Dockerfile copia `target/quarkus-app/` para a imagem. Antes de enviar, execute:

```bash
./mvnw -q -DskipTests compile
bash -n deploy/deploy-backend.sh deploy/backup-postgres.sh
```

Envie e publique a mesma imagem validada:

```bash
scp "casamento-backend-${VERSION}.tar.gz" "casamento-backend-${VERSION}.tar.gz.sha256" SEU_USUARIO@IP_DA_VPS:/opt/casamento/releases/
```

```bash
cd /opt/casamento/releases
sha256sum -c "casamento-backend-${VERSION}.tar.gz.sha256"
sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=casamento-backend:${VERSION}|" /opt/casamento/.env
/opt/casamento/deploy-backend.sh "/opt/casamento/releases/casamento-backend-${VERSION}.tar.gz"
docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml ps
curl -fsS http://127.0.0.1:8080/q/health/live
```

O primeiro inicio cria o banco e executa Flyway. Em falha: `docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml logs --tail=150 app`.

## 7. Publicar frontend

No repositorio `/home/wsl/sistemas/casemento_frontend-`, crie `.env.production` apenas com valores publicos:

```env
VITE_API_BASE_URL=/api/v1
VITE_EVENT_SLUG=casamento-2027
VITE_APP_NAME=Casamento
```

Nao coloque chaves, senhas ou tokens em `VITE_*`: elas ficam publicas no JavaScript. Gere o build:

```bash
pnpm install --frozen-lockfile
pnpm lint
pnpm build
FRONTEND_VERSION=$(date -u +%Y%m%dT%H%M%SZ)
ssh SEU_USUARIO@IP_DA_VPS "mkdir -p /var/www/casamento/releases/${FRONTEND_VERSION}"
rsync -av --delete dist/ SEU_USUARIO@IP_DA_VPS:/var/www/casamento/releases/${FRONTEND_VERSION}/
ssh SEU_USUARIO@IP_DA_VPS "ln -sfn /var/www/casamento/releases/${FRONTEND_VERSION} /var/www/casamento/current"
```

## 8. HTTPS gratuito e Nginx

Instale primeiro o bloco HTTP temporario. Ele permite ao Let's Encrypt validar o dominio:

```bash
sudo cp /tmp/casamento-deploy/nginx/casamento-http.conf /etc/nginx/sites-available/casamento
sudo ln -sfn /etc/nginx/sites-available/casamento /etc/nginx/sites-enabled/casamento
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

Emita o certificado gratuito com e-mail real:

```bash
sudo certbot certonly --webroot -w /var/www/casamento/current -d gustavoemalucasam.net.br --email SEU_EMAIL --agree-tos --no-eff-email
```

Ative o Nginx definitivo somente apos a emissao:

```bash
sudo cp /tmp/casamento-deploy/nginx/casamento.conf /etc/nginx/sites-available/casamento
sudo nginx -t
sudo systemctl reload nginx
sudo systemctl enable --now certbot.timer
sudo certbot renew --dry-run
```

Confirme de fora da VPS:

```bash
curl -I http://gustavoemalucasam.net.br
curl -I https://gustavoemalucasam.net.br
```

O primeiro deve redirecionar. Teste tambem `/save-the-date` e `/admin/login`: o fallback Nginx deve carregar a SPA em acesso direto.

## 9. Backup, monitoramento e atualizacao

O backup local mantem sete dias. Configure destino R2 privado exclusivo, diferente da midia: em `sudo rclone config`, crie o remote S3 `casamento-backups` com endpoint/credenciais R2 do bucket de backup. Depois:

```bash
sudo rclone lsd casamento-backups:
echo 'BACKUP_RCLONE_DEST=casamento-backups:postgres' | sudo tee -a /opt/casamento/.env
/opt/casamento/backup-postgres.sh
sudo crontab -e
```

Adicione ao crontab:

```cron
15 3 * * * /opt/casamento/backup-postgres.sh >> /var/log/casamento-backup.log 2>&1
```

Teste restauracao em banco separado antes do evento. No painel Locaweb, use **Produtos** -> **Servidores** -> **Administrar** -> **Graficos** para acompanhar RAM, CPU e disco.

Para nova versao, repita as secoes 6 e 7. Para rollback do backend, retorne `BACKEND_IMAGE` a tag anterior e execute:

```bash
cd /opt/casamento
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-deps app
```

Para rollback frontend, aponte `current` para a release anterior. Operacao diaria:

```bash
docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml ps
docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml logs -f app
docker stats
df -h
sudo nginx -t
sudo systemctl status certbot.timer
```

## Diagnostico rapido

| Sintoma | Acao |
|---|---|
| Certbot falha | Confirme `dig`, IP, Nginx e portas 80/443 no UFW/painel Locaweb. |
| Erro 502 | Veja logs do app e execute o healthcheck local. |
| Erro 413 | Confirme `client_max_body_size 210M` e recarregue Nginx. |
| API falha | Confirme `/api/v1`, bloco `/api/` e container `app`. |
| Banco falha | Revise `DB_*`, logs do `db` e nunca apague `pg_data`. |
| Midia falha | Revise `R2_PUBLIC_BASE_URL`, dominio publico e credenciais R2. |

## Checklist final

- [ ] DNS aponta para a VPS e HTTP redireciona a HTTPS.
- [ ] `certbot renew --dry-run` passou.
- [ ] Banco e porta 8080 nao estao publicos.
- [ ] `.env` e chaves JWT nao estao no Git e possuem permissoes restritas.
- [ ] Backend, rotas SPA, API e upload foram testados.
- [ ] Backup externo foi criado e restaurado em ambiente de teste.
- [ ] A VPS foi reiniciada e os servicos retornaram.