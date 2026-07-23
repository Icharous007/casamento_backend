# Manual de Configuração do Cloudflare R2

**Aplicação:** casamento-backend  
**Objetivo:** configurar o Cloudflare R2 para armazenar fotos, vídeos e áudios da aplicação com entrega pública controlada por domínio.

---

## 1. Como este backend usa o R2

Hoje o backend envia os arquivos para o R2 via API S3 compatível e depois devolve uma URL pública para o frontend.

Os parâmetros reais usados pela aplicação estão em `src/main/resources/application.properties`:

```properties
app.r2.endpoint=${R2_ENDPOINT:http://localhost:9000}
app.r2.access-key=${R2_ACCESS_KEY:minioadmin}
app.r2.secret-key=${R2_SECRET_KEY:minioadmin}
app.r2.bucket=${R2_BUCKET:casamento}
app.r2.region=${R2_REGION:auto}
app.r2.public-base-url=${R2_PUBLIC_BASE_URL:}
```

### Recomendação para este projeto

Para produção, use esta combinação:

1. Bucket privado para gravação via API S3.
2. Domínio customizado para leitura pública dos arquivos.
3. `R2_PUBLIC_BASE_URL` apontando para esse domínio customizado.
4. `r2.dev` apenas para teste, nunca como URL principal de produção.

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

## 8. Configurar acesso público dos arquivos

Esse é o ponto mais importante para o frontend funcionar bem.

A documentação do R2 oferece dois caminhos:

1. `r2.dev` para uso de desenvolvimento e testes
2. domínio customizado para produção

### O que usar no seu caso

Use:

1. `r2.dev` apenas para validação inicial
2. domínio customizado para produção

A documentação do Cloudflare informa que o `r2.dev`:

1. é rate limited
2. não é indicado para produção
3. pode sofrer throttling de banda
4. não oferece o mesmo nível de cache, WAF e controles que domínio próprio

### 8.1 Habilitar `r2.dev` para testes

1. Abra o bucket no painel R2.
2. Entre em **Settings**.
3. Em **Public Development URL**, clique em **Enable**.
4. Digite `allow` para confirmar.
5. Copie a URL pública gerada.

Exemplo de uso temporário:

```env
R2_PUBLIC_BASE_URL=https://pub-xxxxxxxx.r2.dev
```

Use isso somente para testes rápidos.

### 8.2 Configurar domínio customizado para produção

A documentação oficial informa que o domínio precisa existir na mesma conta Cloudflare do bucket.

#### Pré-requisito

O domínio ou a zona deve estar na Cloudflare.

Se o domínio ainda não estiver gerenciado lá, use:

1. zona completa na Cloudflare, ou
2. partial setup por CNAME, se aplicável

#### Passo a passo

1. No painel R2, abra o bucket.
2. Vá em **Settings**.
3. Na seção **Custom Domains**, clique em **Add**.
4. Informe o subdomínio desejado, por exemplo:

```txt
media.seudominio.com.br
```

5. Clique em **Continue**.
6. Revise o registro DNS que a Cloudflare vai criar.
7. Clique em **Connect Domain**.
8. Aguarde o status mudar de `Initializing` para `Active`.

### Recomendação prática

Use um subdomínio dedicado:

```txt
media.seudominio.com.br
```

Evite servir mídia pelo mesmo host da API.

Separar os hosts ajuda em:

1. cache
2. observabilidade
3. regras de segurança
4. troubleshooting de CORS

### 8.3 Desabilitar `r2.dev` quando o domínio customizado estiver pronto

A documentação alerta que, se `r2.dev` continuar habilitado, o bucket pode seguir público por esse caminho mesmo se você proteger o domínio customizado com WAF ou Access.

Depois de validar o domínio customizado:

1. volte ao bucket
2. abra **Settings**
3. em **Public Development URL**, clique em **Disable**
4. digite `disallow`

Para produção, essa é a configuração correta.

---

## 9. Configurar cache e HTTPS

A documentação do Cloudflare informa que o domínio customizado permite usar:

1. Cloudflare Cache
2. Smart Tiered Cache
3. WAF
4. Access
5. Bot Management

### Recomendação prática

Para mídia pública do casamento:

1. habilite HTTPS no domínio
2. ative `Always Use HTTPS`
3. considere uma regra de cache para arquivos estáticos
4. se quiser cache agressivo, avalie `Cache Everything`

Observação importante da documentação:

1. nem todo tipo de arquivo entra em cache por padrão
2. se quiser cache de todos os objetos, você precisa configurar regra de cache

Para fotos e vídeos públicos, isso normalmente vale a pena.

---

## 10. Configurar CORS do bucket

### Quando CORS é necessário no seu caso

No fluxo atual do projeto, o upload do navegador vai primeiro para o backend, e o backend envia o arquivo ao R2.

Isso significa:

1. para upload atual, o navegador não fala diretamente com o R2
2. então CORS no R2 não é obrigatório para o upload atual
3. mas CORS pode ser útil para leitura de mídia no frontend, especialmente vídeo, áudio e acesso a headers
4. CORS será obrigatório se no futuro você migrar para upload direto navegador -> R2 com presigned URL

### Passo a passo no painel

1. Abra o bucket no painel R2.
2. Vá em **Settings**.
3. Na seção **CORS Policy**, clique em **Add CORS policy**.
4. Vá para a aba **JSON**.
5. Cole a política.
6. Clique em **Save**.

### Política recomendada para leitura pelo frontend

Se o frontend estiver em `https://app.seudominio.com.br`:

```json
[
  {
    "AllowedOrigins": [
      "https://app.seudominio.com.br",
      "http://localhost:5173"
    ],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag", "Content-Length", "Content-Type", "Cache-Control"],
    "MaxAgeSeconds": 3600
  }
]
```

### Política se no futuro houver upload direto com presigned URL

```json
[
  {
    "AllowedOrigins": [
      "https://app.seudominio.com.br",
      "http://localhost:5173"
    ],
    "AllowedMethods": ["GET", "HEAD", "PUT"],
    "AllowedHeaders": ["Content-Type", "*"],
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
R2_PUBLIC_BASE_URL=https://media.seudominio.com.br
```

### O que significa cada variável

1. `R2_ENDPOINT`: endpoint S3 compatível do R2
2. `R2_ACCESS_KEY`: Access Key ID gerado na Cloudflare
3. `R2_SECRET_KEY`: Secret Access Key gerado na Cloudflare
4. `R2_BUCKET`: bucket onde os arquivos serão gravados
5. `R2_REGION`: deve ficar `auto`
6. `R2_PUBLIC_BASE_URL`: domínio público usado nas URLs devolvidas pelo backend

### Valor correto de `R2_PUBLIC_BASE_URL`

Use um destes formatos:

```env
R2_PUBLIC_BASE_URL=https://media.seudominio.com.br
```

ou, temporariamente:

```env
R2_PUBLIC_BASE_URL=https://pub-xxxxxxxx.r2.dev
```

Não coloque barra final.

Correto:

```txt
https://media.seudominio.com.br
```

Errado:

```txt
https://media.seudominio.com.br/
```

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
3. verifique se a URL retornada já vem com o host público esperado

Exemplo esperado:

```txt
https://media.seudominio.com.br/eventos/.../arquivo.jpg
```

Se a URL voltar com host diferente do configurado, revise `R2_PUBLIC_BASE_URL`.

---

## 13. Checklist de validação funcional

Antes de considerar a configuração pronta, valide estes pontos:

1. o bucket existe e está no ambiente correto
2. o token tem acesso apenas ao bucket certo
3. o endpoint está correto
4. `R2_REGION=auto`
5. o domínio customizado está `Active`
6. `R2_PUBLIC_BASE_URL` aponta para o domínio correto
7. o upload de foto funciona
8. o upload de vídeo funciona
9. a URL retornada pelo backend abre no navegador
10. imagem carrega no frontend
11. vídeo ou áudio carrega sem erro de CORS
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
4. domínio customizado `media.seudominio.com.br`
5. `r2.dev` desabilitado após homologação
6. CORS liberando `GET` e `HEAD` para frontend e localhost
7. HTTPS obrigatório

### Backend

```env
R2_ENDPOINT=https://SEU_ACCOUNT_ID.r2.cloudflarestorage.com
R2_ACCESS_KEY=SEU_ACCESS_KEY_ID
R2_SECRET_KEY=SEU_SECRET_ACCESS_KEY
R2_BUCKET=casamento-media-prod
R2_REGION=auto
R2_PUBLIC_BASE_URL=https://media.seudominio.com.br
```

Essa é a configuração mais consistente com a documentação oficial do Cloudflare R2 e com o código atual do seu backend.
