# Manual de deploy na Oracle Cloud Infrastructure (OCI)

Este guia publica o frontend React/Vite e este backend Quarkus em uma instancia Compute OCI Always Free (Ampere A1.Flex, ARM/aarch64), com HTTPS gratuito. O dominio continua registrado/gerenciado na Locaweb; apenas o registro DNS passa a apontar para a OCI.

| Item | Endereco final |
|---|---|
| Site | `https://gustavoemalucasam.net.br` |
| API | `https://gustavoemalucasam.net.br/api/v1` |
| Saude interna | `http://127.0.0.1:8080/q/health/live` |

O Nginx recebe trafego publico nas portas 80/443, serve o frontend e encaminha `/api` ao Quarkus. Backend e PostgreSQL ficam privados em Docker; o frontend ja usa `/api/v1`, portanto nao ha CORS entre navegador e API em producao.

> Este documento assume um deploy do zero na OCI. Nao cobre migracao/cutover de uma VPS ja em producao (por exemplo, a Locaweb VPS descrita em [DEPLOY.md](DEPLOY.md)).

## Diferenca de arquitetura: ARM (aarch64) vs x86_64

A camada Always Free de maior capacidade da OCI (Ampere A1.Flex, ate 4 OCPU e 24 GB de RAM gratis) roda em **ARM/aarch64**, diferente da VPS x86_64 usada no guia Locaweb. Isso afeta apenas o **build da imagem Docker do backend** (secao 7):

- O comentario em [src/main/docker/Dockerfile.jvm](src/main/docker/Dockerfile.jvm) documenta o build atual com `-Djavacpp.platform=linux-x86_64`, usado pelo `MediaVariantService` para empacotar os binarios nativos ffmpeg do JavaCV. Para ARM, o parametro precisa virar `-Djavacpp.platform=linux-arm64`.
- A imagem base `registry.access.redhat.com/ubi8/openjdk-21:1.23` precisa ter variante `linux/arm64`. **Confirme antes do primeiro build** (comando na secao 7). Se nao houver suporte multi-arch, use uma imagem base alternativa multi-arch (por exemplo `eclipse-temurin:21-jre`) so para o deploy Oracle.
- O restante do pipeline (docker-compose, scripts bash, Nginx, certbot) e independente de arquitetura e nao muda.

## 1. Antes de contratar/criar

Voce precisa de: conta OCI (Always Free habilitado), acesso ao DNS de `gustavoemalucasam.net.br` na Locaweb, acesso aos repositorios backend e frontend, e uma conta Cloudflare R2 configurada conforme [R2_SETUP.md](R2_SETUP.md). Ative MFA na conta OCI (obrigatorio para operacoes sensiveis) e na conta Locaweb.

Na maquina de desenvolvimento Linux, instale Git, Docker Engine com suporte a `buildx` (necessario para build cross-arch), Java 21, Node.js 22 e pnpm. Na instancia OCI nao e necessario Java ou Node: ela recebera a imagem Docker e o build estatico.

## 2. Criar a instancia Compute (OCI)

1. Crie a conta OCI e escolha uma regiao proxima do publico do evento (ex.: `sa-saopaulo-1`, se disponivel na sua conta) ou a regiao "home" definida na criacao da conta — a camada Always Free so existe na regiao "home".
2. No menu **Compute -> Instances**, clique em **Create Instance**.
3. **Placement**: mantenha o compartment padrao (ou crie um compartment dedicado ao projeto).
4. **Image and shape**:
   - Imagem: **Canonical Ubuntu 24.04** (variante aarch64/ARM).
   - Shape: clique em **Change shape**, selecione **Ampere -> VM.Standard.A1.Flex**, marque **Always Free eligible** e defina OCPU/RAM. Sugestao inicial: **2 OCPU / 12 GB RAM** (deixa margem para escalar ate 4 OCPU/24 GB, limite agregado do Always Free, sem criar outra instancia).
5. **Networking**: use o wizard padrao "Create new virtual cloud network" na primeira instancia (ele cria VCN, subnet publica, Internet Gateway e route table automaticamente) — veja detalhes e ajustes na secao 3. Deixe **Assign a public IPv4 address** marcado por enquanto; ele sera substituido por um IP reservado na secao 3.
6. **Add SSH keys**: gere a chave localmente e cole a publica aqui, ou faca upload do arquivo `.pub`:

```bash
ssh-keygen -t ed25519 -C "casamento-oracle"
```

7. **Boot volume**: mantenha o tamanho padrao (ou ajuste dentro do limite agregado de 200 GB do Always Free). Nao e necessario criptografia com chave customizada.
8. Clique em **Create**. Aguarde o estado **Running** e anote o IP publico efemero (sera trocado por um reservado a seguir).
9. Teste o acesso SSH com o usuario padrao da imagem Ubuntu (`ubuntu`):

```bash
ssh ubuntu@IP_PUBLICO_TEMPORARIO
```

10. Antes de qualquer atualizacao de risco, a OCI permite criar um backup manual do boot volume em **Storage -> Boot Volumes -> Create Boot Volume Backup**. Backup nao substitui backup externo do banco (secao 10).

## 3. Configurar a rede (VCN)

Se voce usou o wizard da secao 2, a VCN, a subnet publica, o Internet Gateway e a route table `0.0.0.0/0 -> Internet Gateway` ja existem. Confira e ajuste:

1. **Networking -> Virtual Cloud Networks**, abra a VCN criada e confirme:
   - **Internet Gateway** anexado e habilitado.
   - **Route Table** da subnet publica com rota `0.0.0.0/0` apontando para o Internet Gateway.
2. **Security List** (ou crie uma **Network Security Group** dedicada e associe a instancia): configure as regras de **Ingress**:

   | Origem | Protocolo | Porta | Motivo |
   |---|---|---|---|
   | Seu IP publico `/32` (ou `0.0.0.0/0` se o IP variar) | TCP | 22 | SSH |
   | `0.0.0.0/0` | TCP | 80 | HTTP (redirect + validacao Let's Encrypt) |
   | `0.0.0.0/0` | TCP | 443 | HTTPS |

   Nao crie regras para 5432 (Postgres) nem 8080 (Quarkus): eles ficam publicados apenas em `127.0.0.1` dentro da instancia, atras do Nginx.
3. **Egress**: mantenha a regra padrao `0.0.0.0/0` todas as portas (necessaria para `apt`, Docker Hub/registries, R2, Let's Encrypt).
4. **IP reservado**: va em **Networking -> IP Management -> Reserved Public IPs -> Create Reserved Public IP**, depois associe-o a VNIC primaria da instancia (substitui o IP efemero). Isso evita que o IP mude em um stop/start da instancia. Anote esse IP: sera usado no DNS (secao 4).
5. **Firewall interno da instancia (importante, especifico da OCI)**: as imagens Ubuntu da Oracle vem com regras `iptables` pre-configuradas que bloqueiam todo trafego de entrada exceto SSH, **alem** da Security List do VCN. Sem ajustar isso, o Nginx fica inacessivel mesmo com a Security List correta. Antes de habilitar o `ufw` (secao 5), libere 80/443 no iptables nativo ou desative-o em favor do `ufw`:

```bash
sudo ssh ubuntu@IP_RESERVADO
sudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

   Confirme com `sudo iptables -L INPUT -n --line-numbers` que as portas 80/443 aparecem com `ACCEPT` antes das regras `REJECT` finais. Se preferir simplificar, remova o pacote `iptables-persistent`/`netfilter-persistent` e controle tudo via `ufw` (secao 5) — nesse caso confirme que nenhuma regra `iptables` residual continua ativa apos o reboot.

## 4. Apontar DNS (Locaweb)

No provedor que controla os nameservers do dominio (Locaweb), crie/atualize este registro para o **IP reservado da OCI** obtido na secao 3:

| Tipo | Nome | Valor |
|---|---|---|
| `A` | `@` | `IP_RESERVADO_OCI` |

Painel de Hospedagem Locaweb -> dominio -> menu de tres pontos -> **Zona de DNS** -> **Adicionar/editar entrada**. Aguarde a propagacao, que pode levar 4 a 24 horas:

```bash
dig +short gustavoemalucasam.net.br A
```

O resultado precisa ser o IP reservado da OCI antes de emitir o certificado (secao 9).

## 5. Instalar dependencias na instancia

Com o usuario `ubuntu` (ja possui sudo) ou um usuario administrativo criado por voce:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y docker.io docker-compose-v2 nginx certbot python3-certbot-nginx ufw rclone curl
sudo systemctl enable --now docker nginx
sudo usermod -aG docker "$USER"
```

Saia e entre novamente. Valide com `docker --version`, `docker compose version`, `nginx -v` e `certbot --version`. Todos esses pacotes tem build arm64 nos repositorios padrao do Ubuntu.

Somente depois de resolver o iptables interno (secao 3, item 5), habilite o `ufw`:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status verbose
```

## 6. Preparar arquivos e segredos

No repositorio backend local, envie os modelos de deploy:

```bash
rsync -av deploy/ ubuntu@IP_RESERVADO_OCI:/tmp/casamento-deploy/
```

Na instancia:

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

Gere as chaves JWT uma vez na instancia:

```bash
cd /opt/casamento/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out privateKey.pem
openssl rsa -pubout -in privateKey.pem -out publicKey.pem
chmod 600 privateKey.pem
chmod 644 publicKey.pem
```

Antes de divulgar o QR/link, ative explicitamente o evento. O perfil de producao bloqueia novos cadastros em eventos `DRAFT`:

```bash
docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml \
	exec -T db psql -U "$DB_USERNAME" -d "${DB_NAME:-casamento}" \
	-c "UPDATE events SET status = 'ACTIVE' WHERE slug = 'casamento-2027';"
```

Configure tambem o Worker privado e o CORS de upload direto descritos em [R2_SETUP.md](R2_SETUP.md). Em producao, `R2_PUBLIC_BASE_URL` deve ficar vazio e `MEDIA_DELIVERY_BASE_URL`/`MEDIA_DELIVERY_SIGNING_KEY` sao obrigatorios.

## 7. PostgreSQL: configuracao e tuning

O servico `db` de [deploy/docker-compose.prod.yml](deploy/docker-compose.prod.yml) sobe um Postgres 16-alpine com dados persistidos no volume Docker `pg_data` (dentro do boot volume da instancia) e healthcheck via `pg_isready`. Ele nunca expoe a porta 5432 publicamente — so `app` acessa `db` na rede interna `private` do compose.

Os limites de recursos (`cpus`, `mem_limit`) e os parametros de Postgres (`max_connections`, `shared_buffers`, `work_mem`, `maintenance_work_mem`) no arquivo original foram calibrados para uma VPS de 4 GB. Ajuste-os proporcionalmente ao tamanho de instancia escolhido na secao 2:

| Instancia (OCPU/RAM) | `db.mem_limit` | `db.cpus` | `shared_buffers` | `work_mem` | `max_connections` |
|---|---|---|---|---|---|
| 2 OCPU / 12 GB | `1500m` | `1.0` | `384MB` | `4MB` | `60` |
| 4 OCPU / 24 GB | `3000m` | `2.0` | `768MB` | `8MB` | `100` |

Edite os valores diretamente em `/opt/casamento/docker-compose.prod.yml` no bloco `command` e `mem_limit` do servico `db`, mantendo margem para o servico `app` (Quarkus + JVM) e o proprio SO. Como regra geral, `shared_buffers` fica em torno de 20-25% da RAM dedicada ao Postgres, e `mem_limit` do `db` + `mem_limit` do `app` deve deixar pelo menos 1-2 GB livres para o SO e o Nginx.

Depois de qualquer mudanca de tuning, reinicie so o banco:

```bash
cd /opt/casamento
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-deps db
```

**Backup**: o script [deploy/backup-postgres.sh](deploy/backup-postgres.sh) e o `rclone` continuam indo para o Cloudflare R2, sem mudancas — configure conforme a secao 10. Teste a restauracao em um banco separado antes do evento.

## 8. Criar e publicar a imagem Docker do backend (build ARM)

Execute na maquina de desenvolvimento, dentro de `casamento_backend`, nunca na instancia.

1. Confirme que a imagem base tem variante `arm64` antes do primeiro build real:

```bash
docker manifest inspect registry.access.redhat.com/ubi8/openjdk-21:1.23 | grep -A2 arm64
```

   Se o comando nao retornar nada, troque a `FROM` de [src/main/docker/Dockerfile.jvm](src/main/docker/Dockerfile.jvm) por uma imagem multi-arch equivalente (ex.: `eclipse-temurin:21-jre`) apenas para este deploy, ou mantenha um Dockerfile alternativo dedicado a ARM.

2. Habilite o `buildx` (uma vez por maquina) e confirme suporte a `linux/arm64`:

```bash
docker buildx create --use --name casamento-arm-builder
docker buildx inspect --bootstrap
```

3. Empacote a aplicacao com os binarios nativos ffmpeg para ARM:

```bash
./mvnw -q -DskipTests clean package -Djavacpp.platform=linux-arm64
```

4. Antes de enviar, valide o compile e os scripts:

```bash
./mvnw -q -DskipTests compile
bash -n deploy/deploy-backend.sh deploy/backup-postgres.sh
```

5. Construa e exporte a imagem para `linux/arm64` (cross-build via QEMU/buildx a partir de uma maquina dev x86_64):

```bash
VERSION=$(date -u +%Y%m%dT%H%M%SZ)
docker buildx build --platform linux/arm64 \
	-f src/main/docker/Dockerfile.jvm \
	-t "casamento-backend:${VERSION}" \
	--load .
docker image inspect "casamento-backend:${VERSION}" --format '{{.Id}}'
docker save "casamento-backend:${VERSION}" | gzip > "casamento-backend-${VERSION}.tar.gz"
sha256sum "casamento-backend-${VERSION}.tar.gz" > "casamento-backend-${VERSION}.tar.gz.sha256"
```

6. Envie e publique a mesma imagem validada:

```bash
scp "casamento-backend-${VERSION}.tar.gz" "casamento-backend-${VERSION}.tar.gz.sha256" ubuntu@IP_RESERVADO_OCI:/opt/casamento/releases/
```

```bash
cd /opt/casamento/releases
sha256sum -c "casamento-backend-${VERSION}.tar.gz.sha256"
sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=casamento-backend:${VERSION}|" /opt/casamento/.env
/opt/casamento/deploy-backend.sh "/opt/casamento/releases/casamento-backend-${VERSION}.tar.gz"
docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml ps
curl -fsS http://127.0.0.1:8080/q/health/live
```

Se o container falhar ao subir com um erro do tipo `exec format error`, a imagem foi construida para a arquitetura errada (x86_64 em vez de arm64) — repita o passo 5 conferindo `--platform linux/arm64`.

O primeiro inicio cria o banco e executa Flyway. Em falha: `docker compose --env-file /opt/casamento/.env -f /opt/casamento/docker-compose.prod.yml logs --tail=150 app`.

## 9. Publicar frontend

Este passo e identico independentemente da arquitetura da instancia (o build do frontend e estatico). No repositorio `/home/wsl/sistemas/casemento_frontend-`, crie `.env.production` apenas com valores publicos:

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
ssh ubuntu@IP_RESERVADO_OCI "mkdir -p /var/www/casamento/releases/${FRONTEND_VERSION}"
rsync -av --delete dist/ ubuntu@IP_RESERVADO_OCI:/var/www/casamento/releases/${FRONTEND_VERSION}/
ssh ubuntu@IP_RESERVADO_OCI "ln -sfn /var/www/casamento/releases/${FRONTEND_VERSION} /var/www/casamento/current"
```

## 10. HTTPS gratuito e Nginx

Instale primeiro o bloco HTTP temporario. Ele permite ao Let's Encrypt validar o dominio (as portas 80/443 ja devem estar liberadas na Security List e no iptables interno, conforme secao 3):

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

Confirme de fora da instancia:

```bash
curl -I http://gustavoemalucasam.net.br
curl -I https://gustavoemalucasam.net.br
```

O primeiro deve redirecionar. Teste tambem `/save-the-date` e `/admin/login`: o fallback Nginx deve carregar a SPA em acesso direto.

## 11. Backup, monitoramento e atualizacao

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

Teste restauracao em banco separado antes do evento. No Console OCI, use **Compute -> Instances -> sua instancia -> Metrics** para acompanhar CPU, memoria e disco (a metrica de memoria exige o plugin OCI Monitoring Agent, instalado por padrao nas imagens Ubuntu da Oracle).

Para nova versao, repita as secoes 8 e 9. Para rollback do backend, retorne `BACKEND_IMAGE` a tag anterior e execute:

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
| Certbot falha | Confirme `dig`, IP reservado, Security List/NSG e iptables interno nas portas 80/443. |
| Erro 502 | Veja logs do app e execute o healthcheck local. |
| Erro 413 | Confirme `client_max_body_size 210M` e recarregue Nginx. |
| API falha | Confirme `/api/v1`, bloco `/api/` e container `app`. |
| Banco falha | Revise `DB_*`, logs do `db` e nunca apague `pg_data`. |
| Midia falha | Revise `R2_PUBLIC_BASE_URL`, dominio publico e credenciais R2. |
| Container nao inicia (`exec format error`) | Imagem foi construida para x86_64 em vez de `linux/arm64`; refaca o build com `docker buildx --platform linux/arm64`. |
| Site/API inacessiveis mesmo com Security List correta | Confira o iptables interno da instancia (`sudo iptables -L INPUT -n --line-numbers`); libere 80/443 conforme secao 3. |
| IP mudou apos reboot/stop da instancia | O IP nao e Reserved; associe um Reserved Public IP conforme secao 3 e atualize o DNS. |

## Checklist final

- [ ] DNS aponta para o IP reservado da OCI e HTTP redireciona a HTTPS.
- [ ] `certbot renew --dry-run` passou.
- [ ] Banco e porta 8080 nao estao publicos (nem na Security List, nem no iptables interno).
- [ ] Security List/NSG libera apenas 22/80/443.
- [ ] IP publico da instancia e Reserved, nao efemero.
- [ ] Imagem Docker do backend foi construida para `linux/arm64` (nao `linux/amd64`).
- [ ] `.env` e chaves JWT nao estao no Git e possuem permissoes restritas.
- [ ] Backend, rotas SPA, API e upload foram testados.
- [ ] Backup externo foi criado e restaurado em ambiente de teste.
- [ ] A instancia foi reiniciada e os servicos retornaram.
