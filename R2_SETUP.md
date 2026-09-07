# Manual de Configuração do Cloudflare R2

**Aplicação:** casamento-backend  
**Objetivo:** configurar o Cloudflare R2 para armazenar fotos, vídeos e áudios da aplicação com entrega pública controlada por domínio.

---

## 1. Como este backend usa o R2

O backend cria uma URL S3 `PUT` curta e o navegador envia o arquivo diretamente ao R2. A leitura ocorre exclusivamente por um Cloudflare Worker que valida uma assinatura HMAC curta; o bucket não é público.

Os parâmetros reais usados pela aplicação estão em `src/main/resources/application.properties`:

```properties
app.r2.endpoint=${R2_ENDPOINT:http://localhost:9000}
app.r2.access-key=${R2_ACCESS_KEY:minioadmin}
app.r2.secret-key=${R2_SECRET_KEY:minioadmin}
app.r2.bucket=${R2_BUCKET:casamento}
app.r2.region=${R2_REGION:auto}
app.r2.public-base-url=${R2_PUBLIC_BASE_URL:}
app.r2.upload-url-ttl=${R2_UPLOAD_URL_TTL:PT10M}
app.media.delivery-base-url=${MEDIA_DELIVERY_BASE_URL:}
app.media.delivery-signing-key=${MEDIA_DELIVERY_SIGNING_KEY:}
app.media.delivery-url-ttl=${MEDIA_DELIVERY_URL_TTL:PT5M}
```

### Recomendação para este projeto

Para produção, use esta combinação:

1. Bucket privado para gravação pela API S3 assinada.
2. Worker com binding do bucket como única rota de leitura.
3. `MEDIA_DELIVERY_BASE_URL` apontando para a rota do Worker.
4. `R2_PUBLIC_BASE_URL` vazio e `r2.dev` desabilitado, inclusive em testes de produção.

Isso é o que melhor combina com o fluxo atual do sistema.

---

## 2. O que você precisa decidir antes de começar

Antes de abrir o painel do Cloudflare, defina estes valores:

### Nome do bucket

Sugestão:

```txt
casamento-media-prod
```

Ou, se quiser separar ambientes:

```txt
casamento-media-dev
casamento-media-hml
casamento-media-prod
```

### Domínio público dos arquivos

Sugestão:

```txt
media.seudominio.com.br
```

Esse domínio será usado para servir imagens, vídeos e áudios para o frontend.

### Tipo de credencial

A documentação do Cloudflare permite:

1. `Create Account API token`
2. `Create User API token`

Para produção, eu recomendo `Account API token`, porque ele não depende do usuário individual continuar vinculado à conta.

### Localização dos dados

A documentação do R2 informa que o padrão recomendado é `Automatic`.

Use assim:

1. `Automatic` se você quer simplicidade e não tem exigência regulatória.
2. `Jurisdiction` apenas se houver exigência formal de residência de dados, como `eu`.

Para o seu caso, a escolha prática é `Automatic`.

---

## 3. Ativar o R2 na conta Cloudflare

Segundo a documentação oficial:

1. Acesse o painel da Cloudflare.
2. Entre em **Storage & databases**.
3. Abra **R2 > Overview**.
4. Se o R2 ainda não estiver ativo, conclua a ativação da assinatura.

Observação importante:

1. O R2 tem camada gratuita inicial.
2. O faturamento passa a ser por uso.
3. Você precisa do R2 ativo antes de gerar credenciais.

---

## 4. Encontrar o Account ID

Você vai precisar do `ACCOUNT_ID` para montar o endpoint S3.

### Como encontrar

1. No painel da Cloudflare, abra a conta correta.
2. Localize o `Account ID` nas informações da conta.
3. Guarde esse valor.

Você vai usar esse ID para formar o endpoint:

```txt
https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

Se estiver usando bucket com jurisdição, o endpoint muda para:

```txt
https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com
```

ou

```txt
https://<ACCOUNT_ID>.fedramp.r2.cloudflarestorage.com
```

---

## 5. Criar o bucket

### Pelo painel

1. Vá em **R2 object storage**.
2. Clique em **Create bucket**.
3. Informe o nome do bucket.
4. Em **Location**, deixe `Automatic`.
5. Conclua em **Create bucket**.

### Recomendação prática para este projeto

Use um bucket dedicado para mídia da aplicação, por exemplo:

```txt
casamento-media-prod
```

Evite reutilizar o mesmo bucket para:

1. backups do banco
2. arquivos administrativos
3. logs
4. mídia pública do evento

Separar responsabilidades reduz risco operacional.

### Se quiser separar ambientes

Use buckets distintos:

```txt
casamento-media-dev
casamento-media-hml
casamento-media-prod
```

---

## 6. Gerar as credenciais da API S3

A documentação oficial do R2 orienta a gerar um token próprio para a API S3.

### Passo a passo

1. Vá em **Storage & databases > R2 > Overview**.
2. Clique em **Manage** na seção **API Tokens**.
3. Escolha:
   - `Create Account API token`, ou
   - `Create User API token`
4. Em **Permissions**, selecione `Object Read & Write`.
5. Em escopo, escolha `Apply to specific buckets only`.
6. Selecione apenas o bucket da aplicação.
7. Clique em **Create API token**.

### O que salvar nessa etapa

Ao final, a Cloudflare mostra:

1. `Access Key ID`
2. `Secret Access Key`
3. endpoint S3

Guarde os dois primeiros imediatamente.

A documentação avisa que o `Secret Access Key` não poderá ser visualizado novamente.

### Permissão recomendada

Para este backend, use o mínimo necessário:

1. `Object Read & Write`
2. escopo apenas no bucket da aplicação

Você não precisa de permissão administrativa ampla para o app subir e ler objetos.

---

## 7. Definir o endpoint correto

Para buckets normais, o endpoint é:

```txt
https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

### Variável do projeto

```env
R2_ENDPOINT=https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com
```

### Região

A documentação do R2 informa que a região para uso com SDK S3 deve ser:

```txt
auto
```

Então no projeto:

```env
R2_REGION=auto
```

### Importante sobre buckets com jurisdição

Se você criar um bucket com jurisdição específica, o endpoint precisa refletir isso.

Exemplo para `eu`:

```env
R2_ENDPOINT=https://SEU_ACCOUNT_ID.eu.r2.cloudflarestorage.com
R2_REGION=auto
```

A própria documentação observa que buckets jurisdicionais só podem ser acessados pelo endpoint daquela jurisdição.

---

## 8. Configurar entrega privada dos arquivos

Esse é o ponto mais importante para o frontend funcionar bem.

Crie uma rota Worker em um subdomínio dedicado, por exemplo `media.seudominio.com.br/*`. O código está em `deploy/cloudflare/media-worker/` e deve receber o binding privado do bucket.

Antes do deploy, gere um segredo aleatório único e configure o mesmo valor como `MEDIA_DELIVERY_SIGNING_KEY` na VPS e `MEDIA_DELIVERY_SIGNING_KEY` no Worker. Depois:

```bash
cd deploy/cloudflare/media-worker
npx wrangler login
npx wrangler secret put MEDIA_DELIVERY_SIGNING_KEY
npx wrangler r2 bucket cors set casamento-media-prod --file r2-cors.production.json
npx wrangler deploy
```

Desabilite `r2.dev` e não associe um domínio público diretamente ao bucket. A rota do Worker já fornece cache de edge depois de validar a assinatura.

---

## 9. Configurar cache e HTTPS

A documentação do Cloudflare informa que o domínio customizado permite usar:

1. Cloudflare Cache
2. Smart Tiered Cache
3. WAF
4. Access
5. Bot Management

### Recomendação prática

Para mídia privada do casamento:

1. habilite HTTPS no domínio
2. ative `Always Use HTTPS`
3. mantenha o Worker como rota de mídia
4. não crie regra que exponha diretamente o bucket

Observação importante da documentação:

1. nem todo tipo de arquivo entra em cache por padrão
2. se quiser cache de todos os objetos, você precisa configurar regra de cache

Para fotos e vídeos públicos, isso normalmente vale a pena.

---

## 10. Configurar CORS do bucket

### Quando CORS é necessário no seu caso

No fluxo atual do projeto, o navegador recebe uma URL assinada curta e envia o arquivo diretamente ao R2.

Isso significa:

1. CORS no R2 é obrigatório para o `PUT` direto
2. o Worker atende leitura, portanto R2 não precisa liberar `GET` ou `HEAD` ao frontend
3. não use `AllowedOrigins: ["*"]`

### Passo a passo no painel

1. Abra o bucket no painel R2.
2. Vá em **Settings**.
3. Na seção **CORS Policy**, clique em **Add CORS policy**.
4. Vá para a aba **JSON**.
5. Cole a política.
6. Clique em **Save**.

### Política para upload direto com presigned URL

```json
[
  {
    "AllowedOrigins": [
      "https://app.seudominio.com.br",
      "http://localhost:5173"
    ],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["Content-Type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

### Cuidados importantes da documentação

1. `AllowedOrigins` deve conter apenas `scheme://host[:port]`
2. não coloque path
3. `https://site.com` é válido
4. `https://site.com/alguma-rota` é inválido
5. mudanças de CORS podem levar até cerca de 30 segundos para propagar
6. se usar domínio customizado com cache, pode ser necessário fazer purge para refletir os novos headers

---

## 11. Configurar as variáveis da aplicação

Depois que bucket, token e domínio estiverem prontos, preencha o `.env` de produção.

### Exemplo recomendado

```env
R2_ENDPOINT=https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=SEU_ACCESS_KEY_ID
R2_SECRET_KEY=SEU_SECRET_ACCESS_KEY
R2_BUCKET=casamento-media-prod
R2_REGION=auto
R2_PUBLIC_BASE_URL=
MEDIA_DELIVERY_BASE_URL=https://media.seudominio.com.br
MEDIA_DELIVERY_SIGNING_KEY=SEGREDO_ALEATORIO_COMPARTILHADO_COM_O_WORKER
MEDIA_DELIVERY_URL_TTL=PT5M
```

### O que significa cada variável

1. `R2_ENDPOINT`: endpoint S3 compatível do R2
2. `R2_ACCESS_KEY`: Access Key ID gerado na Cloudflare
3. `R2_SECRET_KEY`: Secret Access Key gerado na Cloudflare
4. `R2_BUCKET`: bucket onde os arquivos serão gravados
5. `R2_REGION`: deve ficar `auto`
6. `R2_PUBLIC_BASE_URL`: deve ficar vazio em produção
7. `MEDIA_DELIVERY_BASE_URL`: domínio do Worker usado nas URLs devolvidas pelo backend
8. `MEDIA_DELIVERY_SIGNING_KEY`: segredo HMAC compartilhado com o Worker

### Valor correto de `MEDIA_DELIVERY_BASE_URL`

Use o domínio da rota Worker, sem barra final:

```env
MEDIA_DELIVERY_BASE_URL=https://media.seudominio.com.br
R2_PUBLIC_BASE_URL=
```

O backend exige essa configuração no perfil `prod`; ele não deve montar URLs `r2.dev` ou de domínio público do bucket.

---

## 12. Testar as credenciais antes do deploy final

Eu recomendo validar o bucket antes de subir a aplicação em produção.

### Opção 1: testar com AWS CLI

Exemplo:

```bash
export AWS_ACCESS_KEY_ID="SEU_ACCESS_KEY_ID"
export AWS_SECRET_ACCESS_KEY="SEU_SECRET_ACCESS_KEY"

aws s3 ls \
  --endpoint-url "https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com" \
  --region auto
```

Para listar objetos de um bucket:

```bash
aws s3 ls s3://casamento-media-prod \
  --endpoint-url "https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com" \
  --region auto
```

### Opção 2: testar com o próprio backend

Depois de preencher o `.env` e subir a aplicação:

1. faça upload de uma foto pequena
2. confira se o registro foi salvo normalmente
3. verifique se a URL retornada usa o host do Worker e contém `expires` e `signature`

Exemplo esperado:

```txt
https://media.seudominio.com.br/media/.../arquivo.jpg?expires=...&signature=...
```

Se a URL voltar sem assinatura, revise `MEDIA_DELIVERY_BASE_URL` e `MEDIA_DELIVERY_SIGNING_KEY`.

---

## 13. Checklist de validação funcional

Antes de considerar a configuração pronta, valide estes pontos:

1. o bucket existe e está no ambiente correto
2. o token tem acesso apenas ao bucket certo
3. o endpoint está correto
4. `R2_REGION=auto`
5. a rota Worker está publicada e o binding aponta para o bucket correto
6. `R2_PUBLIC_BASE_URL` está vazio e `MEDIA_DELIVERY_BASE_URL` aponta para o Worker
7. o upload de foto funciona
8. o upload de vídeo funciona
9. a URL retornada pelo backend abre no navegador
10. imagem carrega no frontend
11. vídeo ou áudio carrega pela URL assinada do Worker
12. `r2.dev` foi desabilitado se o ambiente já é produção

---

## 14. Checklist de segurança

A documentação do R2 informa que os objetos já são:

1. criptografados em repouso com AES-256
2. protegidos em trânsito com TLS

Mesmo assim, você ainda deve fazer estas configurações operacionais:

1. usar token com menor privilégio possível
2. restringir o token ao bucket da aplicação
3. nunca versionar `R2_ACCESS_KEY` ou `R2_SECRET_KEY`
4. manter segredos apenas em `.env`, painel secreto ou cofre de segredos
5. usar domínio customizado com HTTPS
6. desabilitar `r2.dev` em produção
7. se precisar restringir acesso, usar WAF Token Authentication ou Cloudflare Access

---

## 15. Limites relevantes para a sua aplicação

Pontos úteis da documentação oficial:

1. tamanho máximo por objeto: até 5 TiB
2. upload single-part: até 5 GiB
3. upload multipart: até 4.995 TiB
4. até 100 domínios customizados por bucket
5. `r2.dev` sofre rate limiting e não é para produção

Para o seu backend, isso significa:

1. o limite do R2 não é o gargalo principal
2. o gargalo real virá antes, na VPS, banda, CPU e throughput do app
3. o upload atual de até 55 MB está muito abaixo dos limites do R2

---

## 16. Troubleshooting rápido

### Erro 403 ao subir arquivo

Verifique nesta ordem:

1. `R2_ENDPOINT` está correto
2. token tem permissão `Object Read & Write`
3. token está no bucket correto
4. `R2_SECRET_KEY` foi copiado corretamente
5. a aplicação foi reiniciada após atualizar as variáveis

### URL abre, mas o frontend não consegue usar

Verifique:

1. se `R2_PUBLIC_BASE_URL` está correto
2. se o domínio customizado está `Active`
3. se existe política CORS adequada
4. se o cache do Cloudflare precisa de purge

### Funciona no painel, mas não em produção

Verifique:

1. se o ambiente de produção recebeu as variáveis novas
2. se o bucket é o mesmo do token
3. se o endpoint é da conta certa
4. se você não deixou `r2.dev` como URL principal por engano

---

## 17. Configuração final recomendada para o seu caso

Se eu fosse deixar este projeto pronto para produção hoje, eu usaria exatamente assim:

### Cloudflare

1. bucket `casamento-media-prod`
2. localização `Automatic`
3. token `Object Read & Write` restrito ao bucket
4. Worker na rota `media.seudominio.com.br/*` com binding do bucket
5. `r2.dev` desabilitado após homologação
6. CORS liberando somente `PUT` do frontend para upload direto
7. HTTPS obrigatório

### Backend

```env
R2_ENDPOINT=https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=SEU_ACCESS_KEY_ID
R2_SECRET_KEY=SEU_SECRET_ACCESS_KEY
R2_BUCKET=casamento-media-prod
R2_REGION=auto
R2_PUBLIC_BASE_URL=
MEDIA_DELIVERY_BASE_URL=https://media.seudominio.com.br
MEDIA_DELIVERY_SIGNING_KEY=SEGREDO_ALEATORIO_COMPARTILHADO_COM_O_WORKER
MEDIA_DELIVERY_URL_TTL=PT5M
```

Essa é a configuração necessária para a entrega privada pelo Worker e upload direto ao R2 implementados no backend.
