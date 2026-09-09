# Guia de Testes: Feature de Confirmação de Presença por Terceiros

## Testes Backend

### Pré-requisitos

A migração V011 deve ser aplicada ao banco de dados ANTES de rodar os testes.

### Para aplicar a migração:

```bash
cd /home/wsl/sistemas/casamento_backend

# Opção 1: Rodar em dev (aplica migrations automaticamente)
./mvnw quarkus:dev

# Opção 2: Criar banco de testes manualmente
# Não implementado - recomendado usar quarkus:dev
```

### Rodar testes de API:

```bash
# Terminal 1: Rodar backend em dev (para aplicar migrations)
./mvnw quarkus:dev

# Terminal 2: Rodar testes (em outro terminal)
./mvnw clean test -Dtest=GuestPartyResourceTest
```

### Testes inclusos:

- **GuestPartyResourceTest** (`src/test/java/br/com/casamento/guest/party/resource/`)
  - ✅ shouldListPartyMembers: Lista self + dependentes
  - ✅ shouldAddChild: Criar criança sem telefone
  - ✅ shouldRejectEmptyName: Rejeitar nome vazio
  - ✅ shouldRejectInvalidGuestType: Rejeitar tipo inválido
  - ✅ shouldRequireAuthentication: Requer token válido

### Testes manuais com curl:

```bash
# 1. Registrar guest para obter token
curl -X POST http://localhost:8080/api/v1/guest-access/register \
  -H "Content-Type: application/json" \
  -d '{
    "eventSlug": "seu-evento",
    "phone": "11987654321",
    "displayName": "Seu Nome",
    "acceptedTerms": true
  }'
# Resposta: { "token": "..." }

# 2. Adicionar criança à família
TOKEN="<copie-aqui>"
curl -X POST http://localhost:8080/api/v1/me/party \
  -H "X-Guest-Access-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "João Silva",
    "phone": null,
    "guestType": "CHILD",
    "age": 7
  }'

# 3. Listar membros da família
curl -X GET http://localhost:8080/api/v1/me/party \
  -H "X-Guest-Access-Token: $TOKEN"

# 4. Confirmar RSVP de um membro
GUEST_ID="<uuid-do-joao>"
curl -X PUT http://localhost:8080/api/v1/me/party/$GUEST_ID/rsvp \
  -H "X-Guest-Access-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "attendanceStatus": "ATTENDING"
  }'

# 5. Remover um membro
curl -X DELETE http://localhost:8080/api/v1/me/party/$GUEST_ID \
  -H "X-Guest-Access-Token: $TOKEN"
```

---

## Testes Frontend

### Setup

```bash
cd /home/wsl/sistemas/casemento_frontend-

# Instalar dependências de teste (se ainda não instaladas)
npm install --save-dev vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom

# Rodar testes
npm run test

# Ou em modo watch
npm run test:watch
```

### Testes inclusos:

**Arquivo:** `src/__tests__/utils/phoneMask.test.ts`
- ✅ maskPhone: Máscara (XX) XXXXX-XXXX funciona
- ✅ unmaskPhone: Remove máscara, deixa apenas dígitos
- ✅ isPhoneLengthValid: Valida comprimento 11 dígitos

**Arquivo:** `src/__tests__/api/partyApi.test.ts`
- ✅ listPartyMembers: GET retorna lista
- ✅ addPartyMember: POST cria novo membro
- ✅ confirmPartyMemberRsvp: PUT confirma RSVP
- ✅ removePartyMember: DELETE remove membro
- ✅ Error handling: Captura erros da API

**Arquivo:** `src/__tests__/pages/PartyPage.test.tsx`
- ✅ Renderiza página com form
- ✅ Adiciona criança ao clicar botão
- ✅ Máscara funciona em tempo real
- ✅ Validação de nome (mínimo 2 caracteres)
- ✅ Validação de telefone (11 dígitos)
- ✅ Confirma RSVP ao clicar toggle
- ✅ Remove membro com confirmação
- ✅ Estados de loading e erro

### Testes manuais com UI:

```bash
# Terminal 1: Rodar dev frontend
npm run dev

# Terminal 2: Rodar dev backend (em outro terminal)
cd /home/wsl/sistemas/casamento_backend
./mvnw quarkus:dev

# Browser: Abrir http://localhost:5173
# → /save-the-date (registrar)
# → /minha-familia (testar adição de dependentes)
```

### Checklist de teste manual:

- [ ] Máscara telefônica: digitar `11987654321` aparece como `(11) 98765-4321`
- [ ] Validação: botão "Adicionar" desabilitado até preencher todos os campos
- [ ] Adicionar criança: clique "Adicionar criança", preencha nome+idade, clique Adicionar
- [ ] Lista atualiza: criança aparece na lista com ícone de criança
- [ ] Confirmar RSVP: clique "Sim, vou!" para confirmar presença
- [ ] Remover: clique trash, dialog aparece, confirme deleção
- [ ] Erros: tente adicionar adulto com telefone inválido → erro traduzido
- [ ] Loading: durante requisição, botão fica desabilitado, spinner aparece
- [ ] Responsivo: teste em mobile (Chrome DevTools mode)

---

## CI/CD

### GitHub Actions (recomendado)

Criar `.github/workflows/test.yml`:

```yaml
name: Tests

on: [push, pull_request]

jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: cd /home/wsl/sistemas/casamento_backend && ./mvnw clean test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'
      - run: cd /home/wsl/sistemas/casemento_frontend- && npm ci && npm run test
```

---

## Próximos Passos

1. ✅ Criar testes backend (API REST)
2. ✅ Criar testes frontend (vitest + testing-library)
3. ⏳ Integrar testes no CI/CD
4. ⏳ Aumentar cobertura de testes (> 80%)

## Suporte

Para dúvidas, consulte:
- Documentação de implementação: `PARTY_MANAGEMENT_IMPLEMENTATION.md`
- Logs de teste: `target/surefire-reports/` (backend) ou console (frontend)
- Guia de API: `PARTY_MANAGEMENT_IMPLEMENTATION.md` seção "Documentação da API"
