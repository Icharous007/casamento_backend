# Frontend Update Guide — Save the Date Self-Registration

**Data:** 2026-07-22
**Versão backend:** branch `main`
**Impacto:** Fluxo de entrada do convidado, tela do QR/link, formulário de perfil, tela admin (reset de senha, convites), dados retornados pelo `/me`

---

## Resumo das mudanças

| Área | Antes | Depois |
|---|---|---|
| **Entrada do convidado** | QR/link → digitar telefone + nome → código OTP via WhatsApp → confirmar código | QR/link (`/save-the-date?event=<slug>`) → digitar telefone + nome + aceite → acesso imediato |
| **Login** | `POST /start` + `POST /verify` (dois passos) | `POST /api/v1/guest-access/register` (um único passo) |
| **Identificação do convidado** | Telefone verificado por OTP | Telefone + nome informados no cadastro (mesmo telefone = mesmo convidado; nome é atualizado se mudar) |
| **Reset de senha do admin** | E-mail com link (`/forgot-password`, `/reset-password`) | Removido. Reset é feito manualmente no banco pela equipe técnica. |
| **Convites/lembretes via WhatsApp** | Endpoints admin de envio de convite | Removidos. O convidado acessa via QR/link, sem necessidade de convite individual. |

---

## 1. Novo fluxo de entrada — QR/link "Save the Date"

O QR code (ou link direto) do evento aponta para:

```
{FRONTEND_URL}/save-the-date?event=casamento-2027
```

O frontend deve ler `event` (slug) da query string.

### Tela: telefone + nome + aceite de termos

**Request:** `POST /api/v1/guest-access/register` (sem autenticação)

```json
{
  "eventSlug": "casamento-2027",
  "phone": "11999998888",
  "displayName": "João Silva",
  "acceptedTerms": true
}
```

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `eventSlug` | string | ✅ | Slug do evento (lido da URL) |
| `phone` | string | ✅ | Qualquer formato aceito (ex: `11 9 9999-8888`, `+5511999998888`). O backend normaliza para E.164. |
| `displayName` | string | ✅ | Nome ou apelido exibido na plataforma |
| `acceptedTerms` | boolean | ✅ | Deve ser `true` para prosseguir |

**Response 200 — acesso concedido imediatamente, sem OTP:**
```json
{
  "guestId": "uuid-do-convidado",
  "eventId": "uuid-do-evento",
  "displayName": "João Silva",
  "requiresProfileCompletion": false,
  "accessToken": "raw-token-64-chars",
  "event": {
    "title": "Casamento",
    "coupleNames": "Noivo & Noiva",
    "eventStartAt": "2027-01-30T19:00:00-03:00",
    "rsvpDeadlineAt": "2027-01-15T23:59:59-03:00",
    "venueName": "Local do Evento",
    "venueAddress": "Endereço do evento"
  }
}
```

**Importante:**
- Salvar `accessToken` (ex: `localStorage` ou cookie httpOnly).
- Enviar em **todas** as chamadas autenticadas no header:
  ```
  X-Guest-Access-Token: <accessToken>
  ```
- Após o cadastro, redirecionar diretamente para a área do convidado (RSVP, Presentes, Mural, Galeria) — não há mais tela de código OTP nem tela intermediária de "completar perfil".

**Mesmo telefone retornando:** se o convidado já existe (mesmo `eventSlug` + `phone`), o backend apenas atualiza o `displayName` (se diferente) e retorna um novo `accessToken`. Não há erro de "já cadastrado".

**Erros possíveis:**

| Código HTTP | `code` | Causa |
|---|---|---|
| 400 | `TERMS_NOT_ACCEPTED` | `acceptedTerms: false` |
| 400 | `PHONE_INVALID` | Número inválido ou curto demais |
| 400 | `GUEST_BLOCKED` | Convidado foi bloqueado pelo admin |
| 404 | — | Slug de evento não existe |
| 429 | — | Rate limit (10 req/min por IP) |

---

## 2. Endpoints removidos

Os seguintes endpoints **não existem mais** e devem ser removidos do frontend:

| Endpoint removido | Substituto |
|---|---|
| `POST /api/v1/guest-access/start` | `POST /api/v1/guest-access/register` |
| `POST /api/v1/guest-access/verify` | `POST /api/v1/guest-access/register` |
| `POST /api/v1/guest-access/resolve` | `POST /api/v1/guest-access/register` |
| `POST /api/v1/admin/auth/forgot-password` | Removido — reset manual pela equipe técnica |
| `POST /api/v1/admin/auth/reset-password` | Removido — reset manual pela equipe técnica |
| `POST /api/v1/admin/guests/{guestId}/send-invite` | Removido — sem envio de convite individual |
| `POST /api/v1/admin/guests/send-invites` | Removido — sem envio de convite em lote |
| `GET /api/v1/whatsapp/webhook` / `POST /api/v1/whatsapp/webhook` | Removido |

---

## 3. Dados do convidado autenticado — `GET /api/v1/me`

Sem mudanças na estrutura da resposta (continua retornando `phone`, não `email`):

```json
{
  "guestId": "uuid-do-convidado",
  "displayName": "João Silva",
  "phone": "+5511999998888",
  "rsvpStatus": "PENDING",
  "event": {
    "title": "Casamento",
    "coupleNames": "Noivo & Noiva",
    "eventStartAt": "2027-01-30T19:00:00-03:00",
    "rsvpDeadlineAt": "2027-01-15T23:59:59-03:00",
    "venueName": "Local do Evento",
    "venueAddress": "Endereço do evento"
  }
}
```

---

## 4. RSVP — `PUT /api/v1/me/rsvp`

Sem mudança na interface. A confirmação de RSVP **não** dispara mais mensagem de WhatsApp — o backend apenas salva a resposta.

---

## 5. QR Code — área admin

O QR code do evento agora aponta para a página Save the Date:

```
{FRONTEND_URL}/save-the-date?event=casamento-2027
```

| Endpoint | Antes | Depois |
|---|---|---|
| `GET /api/v1/admin/guests/qr-code` | `?event=<slug>` apontando para `/acesso` | `?event=<slug>` apontando para `/save-the-date` |
| `GET /api/v1/admin/guests/{id}/qr-code` (legado) | `/acesso?event=<slug>` | `/save-the-date?event=<slug>` |
| `GET /api/v1/admin/guests/qr-codes/export` | ZIP com QR único do evento (`/acesso`) | ZIP com QR único do evento (`/save-the-date`) |

---

## 6. Importação de convidados (admin) — sem mudanças

O CSV/XLSX continua no formato `nome,telefone`. A coluna `telefone` é opcional; convidados importados sem telefone precisarão se auto-cadastrar informando o telefone na tela Save the Date (o backend vincula pelo telefone informado, criando um novo registro se não houver correspondência).

---

## 7. Reset de senha do admin — removido

Não há mais fluxo de "esqueci minha senha" por e-mail. Caso um admin esqueça a senha, a equipe técnica deve gerá-la manualmente (hash BCrypt) e atualizar diretamente no banco de dados. Remover do frontend qualquer tela/link de "Esqueci minha senha".

---

## 8. Diagrama do novo fluxo de acesso

```
Convidado escaneia QR / abre link
        │
        ▼
/save-the-date?event=casamento-2027
        │
        ▼
Tela: telefone + nome/apelido + aceitar termos
        │
        ▼
POST /api/v1/guest-access/register
        │
        ▼
accessToken retornado imediatamente
        │
        ▼
Área do convidado
(X-Guest-Access-Token em todas as chamadas)
        │
        ├──▶ RSVP (confirmar presença)
        ├──▶ Presentes
        ├──▶ Mural de recados
        └──▶ Galeria de fotos
```

---

## 9. Checklist de migração frontend

- [ ] Criar/atualizar página `/save-the-date` que lê `?event=<slug>` da URL
- [ ] Substituir as telas de telefone+OTP (dois passos) por uma única tela: telefone + nome + aceite de termos
- [ ] Chamar `POST /api/v1/guest-access/register` e armazenar `accessToken` retornado
- [ ] Remover completamente as chamadas a `/start`, `/verify` e `/resolve`
- [ ] Remover tela/lógica de digitação de código OTP
- [ ] Remover tela de "completar perfil" pós-login (não é mais necessária; `requiresProfileCompletion` sempre `false`)
- [ ] Após `register`, navegar diretamente para a área do convidado (RSVP, Presentes, Mural, Galeria)
- [ ] Remover tela/link "Esqueci minha senha" da área admin
- [ ] Remover botões "Enviar convite WhatsApp" (individual e em lote) da área admin
- [ ] Atualizar qualquer texto/rota que referencie `/acesso` para `/save-the-date`
