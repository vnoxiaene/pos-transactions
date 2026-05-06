# POS Transactions API

API HTTP cloud-native para processamento de transações POS (Point of Sale), construída com Java 21 e Spring Boot 3.3.x.

---

## Sumário

1. [Visão Geral da Arquitetura](#1-visão-geral-da-arquitetura)
2. [Endpoints](#2-endpoints)
3. [Segurança — HMAC SHA-256](#3-segurança--hmac-sha-256)
4. [Idempotência](#4-idempotência)
5. [Resiliência — Anti-Cascade](#5-resiliência--anti-cascade)
6. [Observabilidade](#6-observabilidade)
7. [Como Executar](#7-como-executar)
8. [Testes](#8-testes)
9. [Collection Postman](#9-collection-postman)

---

## 1. Visão Geral da Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                         POS / Client                            │
│              (X-Signature + X-Timestamp + body)                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   POS Transactions API                           │
│                                                                  │
│  ┌──────────────┐    ┌───────────────┐    ┌──────────────────┐  │
│  │ HMAC Filter  │───▶│  Controller   │───▶│ TransactionService│  │
│  └──────────────┘    └───────────────┘    └────────┬─────────┘  │
│                                                    │             │
│  ┌──────────────┐                        ┌────────▼─────────┐  │
│  │ CorrelationId│                        │  Resilience4j     │  │
│  │ Filter (MDC) │                        │  (CB+Retry+BH+TL) │  │
│  └──────────────┘                        └────────┬─────────┘  │
│                                                    │             │
└───────────────────────────────────────────────────┼─────────────┘
                                                    │
                     ┌──────────────────────────────┼──────────────┐
                     │                              ▼              │
                     │  ┌─────────────────────────────────────┐   │
                     │  │       External Payment API (Mock)    │   │
                     │  └─────────────────────────────────────┘   │
                     │                                             │
                     │  ┌─────────────────────────────────────┐   │
                     │  │       PostgreSQL (transactions)       │   │
                     │  └─────────────────────────────────────┘   │
                     └─────────────────────────────────────────────┘
```

### Princípios aplicados

| Princípio | Implementação |
|-----------|---------------|
| **DDD** | Entidade `Transaction` com estados de domínio, lógica de negócio na Service Layer |
| **SOLID** | Interfaces separadas (`ExternalPaymentService`), responsabilidade única por classe |
| **Idempotência distribuída** | Constraint `UNIQUE (terminal_id, nsu)` no PostgreSQL — sem estado local em memória |
| **Cloud-native** | Stateless, compatível com múltiplos pods/Kubernetes |
| **Resiliência** | Circuit Breaker + Retry + Bulkhead + TimeLimiter (Resilience4j) |
| **Segurança** | HMAC-SHA256 por requisição + validação de janela temporal |
| **Observabilidade** | Correlation ID obrigatório + OpenTelemetry via Micrometer |

---

## 2. Endpoints

### 2.1 Autorizar Transação

```http
POST /v1/pos/transactions/authorize
Content-Type: application/json
X-Timestamp: 1715000000
X-Signature: <hmac-sha256>

{
  "nsu": "123456",
  "amount": 199.90,
  "terminalId": "T-1000"
}
```

**Response 200 OK:**
```json
{
  "nsu": "123456",
  "amount": 199.90,
  "terminalId": "T-1000",
  "transactionId": "A1B2C3D4E5F6..."
}
```

**Idempotência:** mesma combinação `(terminalId + nsu)` retorna o mesmo `transactionId` sem duplicar na API externa.

---

### 2.2 Confirmar Transação

```http
POST /v1/pos/transactions/confirm
Content-Type: application/json

{
  "transactionId": "A1B2C3D4E5F6..."
}
```

**Response:** `204 No Content`

**Idempotência:** chamada repetida retorna 204 sem reprocessar.

---

### 2.3 Desfazer Transação (Void)

```http
POST /v1/pos/transactions/void
Content-Type: application/json
```

**Forma A — por transactionId:**
```json
{ "transactionId": "A1B2C3D4E5F6..." }
```

**Forma B — por nsu + terminalId:**
```json
{ "nsu": "123456", "terminalId": "T-1000" }
```

**Response:** `204 No Content`

**Idempotência:** chamada repetida retorna 204 sem reprocessar.

---

### Códigos de Erro

| Código | Situação |
|--------|----------|
| `400`  | Campos obrigatórios ausentes ou inválidos |
| `401`  | Assinatura HMAC inválida ou timestamp expirado |
| `404`  | Transação não encontrada |
| `422`  | Estado inválido (ex: confirmar transação já desfeita) |
| `503`  | Circuit Breaker aberto (API externa indisponível) |

---

## 3. Segurança — HMAC SHA-256

Cada requisição deve conter:

| Header | Descrição |
|--------|-----------|
| `X-Timestamp` | Unix timestamp em segundos (janela válida: ±5 minutos) |
| `X-Signature` | HMAC-SHA256 hexadecimal de `timestamp.body` |

### Como gerar a assinatura

```bash
TIMESTAMP=$(date +%s)
BODY='{"nsu":"123456","amount":199.90,"terminalId":"T-1000"}'
SECRET="my-super-secret-key-change-in-production"

SIGNATURE=$(echo -n "${TIMESTAMP}.${BODY}" | openssl dgst -sha256 -hmac "${SECRET}" | awk '{print $2}')

curl -X POST http://localhost:8080/v1/pos/transactions/authorize \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: ${TIMESTAMP}" \
  -H "X-Signature: ${SIGNATURE}" \
  -d "${BODY}"
```

### Proteções implementadas

- **Requisições forjadas**: sem a chave secreta não é possível gerar assinatura válida
- **Replay attacks**: timestamp fora da janela de 5 minutos é rejeitado
- **Configuração**: `security.hmac.enabled=false` para desabilitar em testes

---

## 4. Idempotência

A idempotência é garantida a nível de banco de dados via `UNIQUE CONSTRAINT (terminal_id, nsu)`, sem depender de cache local em memória.

Isso é fundamental para ambientes com **múltiplos pods/instâncias** (Kubernetes):

```
Pod 1 recebe: POST /authorize {nsu: "123", terminalId: "T-1"}
Pod 2 recebe: POST /authorize {nsu: "123", terminalId: "T-1"}  ← mesma requisição

Ambos consultam o PostgreSQL → constraint garante unicidade → mesmo transactionId retornado
```

### Comportamento por estado

| Operação | Estado atual | Comportamento |
|----------|-------------|---------------|
| `authorize` | Não existe | Cria nova transação |
| `authorize` | Já existe | Retorna transação existente (sem chamar API externa) |
| `confirm` | `AUTHORIZED` | Confirma e chama API externa |
| `confirm` | `CONFIRMED` | No-op (204) |
| `confirm` | `VOIDED` | Erro 422 |
| `void` | `AUTHORIZED` ou `CONFIRMED` | Desfaz e chama API externa |
| `void` | `VOIDED` | No-op (204) |

---

## 5. Resiliência — Anti-Cascade

Toda comunicação com a API externa é protegida por **4 camadas de resiliência** via Resilience4j:

### 5.1 Circuit Breaker

```yaml
slidingWindowSize: 10          # janela de 10 requisições
minimumNumberOfCalls: 5        # mínimo para calcular taxa de falha
failureRateThreshold: 50       # abre quando ≥ 50% falham
slowCallRateThreshold: 80      # considera chamadas lentas como falha
slowCallDurationThreshold: 2s  # limiar de "chamada lenta"
waitDurationInOpenState: 30s   # permanece aberto por 30s
permittedNumberOfCallsInHalfOpenState: 3  # testa com 3 chamadas no half-open
```

**Sinais que abrem o circuit breaker:**
- Taxa de erro ≥ 50% na janela deslizante
- Taxa de chamadas lentas (>2s) ≥ 80%
- Exceções: `IOException`, `TimeoutException`, `ResourceAccessException`

**Quando aberto:** retorna `503 Service Unavailable` imediatamente (fail-fast), sem chamar a API externa.

**Fechamento automático:** após 30s em `OPEN`, entra em `HALF_OPEN` e testa 3 chamadas. Se ≥60% tiverem sucesso, fecha.

### 5.2 Retry (com Backoff Exponencial)

```yaml
maxAttempts: 3
waitDuration: 500ms
enableExponentialBackoff: true
exponentialBackoffMultiplier: 2
# Tentativas: 500ms → 1s → 2s
```

**Anti-retry storm:**
- Máximo de 3 tentativas por requisição
- Backoff exponencial evita sobrecarga da dependência degradada
- `CircuitBreakerOpenException` e erros de negócio **não geram retry**

### 5.3 Bulkhead (Limite de Concorrência)

```yaml
maxConcurrentCalls: 10    # máximo de chamadas simultâneas à API externa
maxWaitDuration: 100ms    # tempo máximo de espera por um slot
```

**Proteção de recursos locais:** impede que uma dependência lenta consuma todas as threads do pool, protegendo CPU e pool de conexões do banco de dados.

### 5.4 TimeLimiter (Timeout)

```yaml
timeoutDuration: 3s
cancelRunningFuture: true
```

**Sem "pendurar" requisições:** toda chamada externa tem timeout de 3 segundos. Após isso, é cancelada e tratada como falha.

### Fluxo de decisão

```
Request → TimeLimiter (3s) → Bulkhead (≤10 concorrentes) → CircuitBreaker → Retry (3x) → External API
               ↓                        ↓                         ↓
           Timeout → 503           Sem slot → 503           CB Aberto → 503
```

---

## 6. Observabilidade

### Correlation ID

Toda requisição recebe (ou gera) um `X-Correlation-Id` único:
- Propagado via header `X-Correlation-Id`
- Disponível em todos os logs via MDC (`correlationId`)
- Retornado no header da resposta para rastreabilidade end-to-end

### Logs estruturados

```
2026-05-06 01:30:00 [http-nio-8080-exec-1] [correlationId=abc-123] INFO  TransactionService - [AUTHORIZE] Processando autorização: terminalId=T-1000, nsu=123456
```

### Actuator Endpoints

| Endpoint | Descrição |
|----------|-----------|
| `GET /actuator/health` | Health geral + status do Circuit Breaker |
| `GET /actuator/health/circuitBreakers` | Status detalhado do CB |
| `GET /actuator/metrics` | Métricas Micrometer |
| `GET /actuator/prometheus` | Métricas no formato Prometheus |

---

## 7. Como Executar

### Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### Com Docker Compose (recomendado)

```bash
# Build
mvn clean package -DskipTests

# Subir PostgreSQL + API
docker-compose up -d

# Verificar status
curl http://localhost:8080/actuator/health
```

### Apenas o banco de dados (desenvolvimento local)

```bash
# Subir apenas o PostgreSQL
docker-compose up -d postgres

# Executar a aplicação localmente
mvn spring-boot:run
```

### Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/pos_transactions` | URL do banco |
| `DATABASE_USERNAME` | `pos_user` | Usuário do banco |
| `DATABASE_PASSWORD` | `pos_password` | Senha do banco |
| `HMAC_SECRET` | `my-super-secret-key-change-in-production` | Chave secreta HMAC |
| `HMAC_ENABLED` | `true` | Habilitar/desabilitar validação HMAC |

> ⚠️ **Em produção**: substitua `HMAC_SECRET` por um segredo forte armazenado em AWS Secrets Manager ou Kubernetes Secrets.

---

## 8. Testes

```bash
# Executar todos os testes
mvn test

# Apenas testes unitários
mvn test -Dtest="**/unit/**"

# Apenas testes BDD (Cucumber)
mvn test -Dtest="CucumberTestRunner"
```

### Cobertura

| Suite | Testes | Cenários |
|-------|--------|----------|
| Unitário — `TransactionService` | 10 | authorize, confirm, void, idempotência, erros |
| Unitário — `HmacSignatureFilter` | 5 | assinatura válida/inválida, timestamp, replay |
| BDD Cucumber — `authorize_transaction.feature` | 2 | nova transação, idempotência |
| BDD Cucumber — `confirm_transaction.feature` | 2 | confirmação, idempotência |
| BDD Cucumber — `void_transaction.feature` | 2 | void, idempotência |
| **Total** | **21** | |

Os testes BDD usam H2 em modo PostgreSQL com `@SpringBootTest`, validando o fluxo completo sem dependência de infraestrutura externa.

---

## 9. Collection Postman

O arquivo `pos-transactions-collection.json` contém a collection completa com:

- Pre-request scripts que geram automaticamente `X-Signature` e `X-Timestamp`
- Testes automatizados para todos os endpoints
- Cenários de idempotência
- Validações de erro (404, 400)
- Health check e status do Circuit Breaker

**Importar no Postman:**
1. Abrir Postman → Import
2. Selecionar `pos-transactions-collection.json`
3. Ajustar a variável `baseUrl` se necessário (padrão: `http://localhost:8080`)
4. Executar "Run Collection" para validar toda a cadeia

---

## Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/pos/transactions/
│   │   ├── PosTransactionsApplication.java
│   │   ├── config/
│   │   │   ├── CachedBodyHttpServletRequest.java  # Wrapper para reler body
│   │   │   ├── CorrelationIdFilter.java           # Filtro Correlation ID + MDC
│   │   │   ├── HmacSignatureFilter.java           # Filtro segurança HMAC-SHA256
│   │   │   ├── Resilience4jConfig.java            # Configuração Resilience4j
│   │   │   └── SecurityConfig.java                # Spring Security stateless
│   │   ├── controller/
│   │   │   ├── TransactionController.java         # Endpoints REST
│   │   │   └── GlobalExceptionHandler.java        # Tratamento centralizado de erros
│   │   ├── domain/
│   │   │   ├── Transaction.java                   # Entidade JPA
│   │   │   └── TransactionStatus.java             # Enum de estados
│   │   ├── dto/                                   # Request/Response DTOs
│   │   ├── exception/                             # Exceções de domínio
│   │   ├── repository/
│   │   │   └── TransactionRepository.java         # Spring Data JPA
│   │   └── service/
│   │       ├── ExternalPaymentService.java        # Interface da API externa
│   │       ├── ExternalPaymentServiceImpl.java    # Mock + Resilience4j
│   │       └── TransactionService.java            # Lógica de negócio
│   └── resources/
│       ├── application.yml
│       └── db/migration/
│           └── V1__create_transactions.sql        # Flyway migration
└── test/
    ├── java/com/pos/transactions/
    │   ├── bdd/                                   # Cucumber BDD
    │   └── unit/                                  # JUnit 5 + Mockito
    └── resources/
        └── features/                              # Cenários Gherkin
```
