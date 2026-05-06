# Arquitetura — Domain-Driven Design (DDD)

Análise arquitetural da plataforma POS Transactions usando conceitos de Domain-Driven Design.

---

## 1. Visão Geral

A plataforma é organizada como um **monorepo Maven com 3 módulos independentes**, alinhados com 3 Bounded Contexts distintos:

### Módulos

| Módulo | Tipo | Context | Responsabilidade |
|--------|------|---------|------------------|
| **auth-commons** | JAR Library | Security & Compliance | Validação HMAC-SHA256 compartilhada |
| **pos-transactions-api** | Spring Boot Service | POS Transaction Context | Orquestração e persistência de transações |
| **external-payment-mock** | Spring Boot Service | External Payment Context | Simulação de adquirente/processadora |

---

## 2. Bounded Contexts

### 2.1 POS Transaction Context (Core Domain)

**Módulo**: `pos-transactions-api`  
**Responsabilidade**: Gerenciar ciclo de vida de transações POS

#### Agregado Raiz: `Transaction`

```java
Transaction {
  UUID id                     // Identidade técnica
  String transactionId        // Business ID (externamente visível)
  String nsu                  // Unique Reference per terminal
  String terminalId           // Terminal identifier
  BigDecimal amount           // Valor da transação
  TransactionStatus status    // AUTHORIZED | CONFIRMED | VOIDED
  Instant createdAt           // Auditoria
  Instant updatedAt           // Auditoria
  Long version                // Optimistic locking
}
```

#### Value Objects

**`TransactionStatus` (Enum)**
- Estados: `AUTHORIZED`, `CONFIRMED`, `VOIDED`
- Imutável, sem identidade
- Encapsula transições de estado válidas

#### Repositório

**`TransactionRepository`** (Spring Data JPA)
- `findByTransactionId(String)` — Lookup por business ID
- `findByTerminalIdAndNsu(String, String)` — Lookup para idempotência

#### Serviço de Domínio

**`TransactionService`**
- Orquestra operações em `Transaction`
- Garante invariantes:
  - Transação não pode transicionar VOIDED → AUTHORIZED
  - Transação VOIDED não pode ser confirmada
  - Idempotência via constraint único `(terminal_id, nsu)`

#### Operações (Use Cases)

1. **AUTHORIZE**: Cria transação com status `AUTHORIZED`, chama serviço externo
2. **CONFIRM**: Transiciona `AUTHORIZED` → `CONFIRMED`, idempotente
3. **VOID**: Transiciona → `VOIDED`, idempotente

#### DTOs (Anti-Corruption Layer)

- `AuthorizeRequest` — Input do cliente
- `AuthorizeResponse` — Output para cliente
- `ConfirmRequest`, `VoidRequest` — Inputs adicionais

---

### 2.2 External Payment Context (Subdomain)

**Módulo**: `external-payment-mock`  
**Responsabilidade**: Simular comportamento de adquirente/processadora

#### Contrato de Integração

**Endpoints do Mock**:
- `POST /api/payment/authorize` — Autorização
- `POST /api/payment/confirm` — Confirmação
- `POST /api/payment/void` — Desfazimento

#### DTOs (Isolados)

- `AuthorizePaymentRequest` — Payload de autorização
- `TransactionPaymentRequest` — Payload de transação
- `PaymentResponse` — Resposta padrão

#### Anti-Corruption Layer

**`ExternalPaymentService` (Interface)**
```java
public interface ExternalPaymentService {
  void authorize(String transactionId, String terminalId, String nsu, BigDecimal amount);
  void confirm(String transactionId);
  void voidTransaction(String transactionId);
}
```

**`ExternalPaymentServiceImpl` (Implementação)**
- Comunica via RestClient (HTTP)
- Transformação de DTOs local → remoto
- Aplicação de padrões de resiliência:
  - Circuit Breaker (50% failure threshold)
  - Retry (até 3 tentativas com backoff exponencial)
  - Bulkhead (10 chamadas concorrentes max)
  - Timeout (3s)

---

### 2.3 Security & Compliance Context (Cross-Cutting)

**Módulo**: `auth-commons` (JAR library compartilhada)  
**Responsabilidade**: Validação de integridade de requisições

#### Componentes

**`HmacSignatureFilter`**
- Valida headers `X-Timestamp` e `X-Signature` em toda requisição
- Algoritmo: HmacSHA256
- Payload: `timestamp.body`
- Janela de tolerância: ±300 segundos

**`HmacSignatureProperties`**
- Configuração via `security.hmac.secret` e `security.hmac.enabled`
- Injeção de dependência

**`CachedBodyHttpServletRequest`**
- Wrapper para permitir múltiplas leituras do request body
- Necessário para HMAC validation + posterior desserialização

---

## 3. Padrões Aplicados

### Domain-Driven Design

- ✅ **Aggregate Root**: `Transaction` encapsula estado e invariantes
- ✅ **Value Objects**: `TransactionStatus` é imutável
- ✅ **Repository Pattern**: `TransactionRepository` abstrai persistência
- ✅ **Bounded Contexts**: 3 contextos bem delimitados
- ✅ **Anti-Corruption Layer**: `ExternalPaymentService` isola integração

### Enterprise Patterns

- ✅ **Service Layer**: `TransactionService` orquestra lógica
- ✅ **DTO Pattern**: Separação entre request/response e domain models
- ✅ **Filter Chain**: Múltiplos filtros compostos (HMAC, Correlation ID)
- ✅ **Global Exception Handler**: Tratamento centralizado de erros

### Cloud-Native Patterns

- ✅ **Circuit Breaker**: Proteção contra cascata de falhas
- ✅ **Retry with Backoff**: Recuperação automática de falhas transitórias
- ✅ **Bulkhead**: Isolamento de recursos
- ✅ **Observability**: OpenTelemetry + Micrometer
- ✅ **Health Checks**: Liveness e readiness via Actuator

---

## 4. Fluxos Principais

### 4.1 AUTHORIZE

```
Client
  ↓ (POST /v1/pos/transactions/authorize com HMAC)
HmacSignatureFilter
  ↓ (valida X-Signature + X-Timestamp)
TransactionController.authorize()
  ↓
TransactionService.authorize(request)
  ├─ findByTerminalIdAndNsu() → Idempotência
  │  └─ Se existe: return existing
  ├─ ExternalPaymentService.authorize() → Chama mock
  │  └─ Resilience4j (Circuit Breaker, Retry, Bulkhead, Timeout)
  ├─ Transaction.builder().status(AUTHORIZED).build()
  ├─ transactionRepository.save()
  └─ return AuthorizeResponse
```

### 4.2 CONFIRM

```
Client
  ↓ (POST /v1/pos/transactions/confirm com HMAC)
HmacSignatureFilter
  ↓ (valida)
TransactionController.confirm()
  ↓
TransactionService.confirm(request)
  ├─ findByTransactionId() → Busca transação
  ├─ Valida estado (deve ser AUTHORIZED)
  ├─ ExternalPaymentService.confirm() → Chama mock
  ├─ transaction.setStatus(CONFIRMED)
  ├─ transactionRepository.save()
  └─ return 204 No Content
```

### 4.3 VOID

```
Client
  ↓ (POST /v1/pos/transactions/void com HMAC)
HmacSignatureFilter
  ↓ (valida)
TransactionController.void()
  ↓
TransactionService.voidTransaction(request)
  ├─ resolveTransaction() → Busca por ID ou (terminalId + nsu)
  ├─ ExternalPaymentService.voidTransaction() → Chama mock
  ├─ transaction.setStatus(VOIDED)
  ├─ transactionRepository.save()
  └─ return 204 No Content
```

---

## 5. Idempotência

### Estratégia: Natural via Constraint Único

```sql
CREATE TABLE transactions (
  ...
  UNIQUE(terminal_id, nsu)
)
```

### Implementação em Code

```java
// TransactionService.authorize()
return transactionRepository
    .findByTerminalIdAndNsu(request.getTerminalId(), request.getNsu())
    .map(existing -> {
        log.info("Idempotência: transação já existe");
        return toAuthorizeResponse(existing);  // Retorna existing sem re-processar
    })
    .orElseGet(() -> createNewTransaction(request));  // Cria nova se não existe
```

### Idempotência em Confirm/Void

```java
// Ambos (confirm e void) são idempotentes
if (transaction.getStatus() == TransactionStatus.CONFIRMED) {
    log.info("Idempotência: já confirmado");
    return;  // Sem erro, sem re-processing
}
```

---

## 6. Resiliência

### Padrões Aplicados

**Circuit Breaker**
- Threshold: 50% failure rate em janela de 10 requisições
- Wait duration: 30 segundos antes de retry
- Fallback: `CircuitBreakerOpenException` → HTTP 503

**Retry**
- Attempts: 3 (original + 2 retries)
- Backoff: Exponencial (500ms → 1s → 2s)
- Aplicado a: `ExternalPaymentService`

**Bulkhead**
- Max concurrent calls: 10
- Max wait duration: 100ms
- Isolação de thread pool

**Timeout**
- Read + Connect: 3s
- Aplicado a: RestClient para mock externo

---

## 7. Observabilidade

### Correlation ID

**`CorrelationIdFilter`**
- Gera UUID único por requisição
- Injeção em MDC (Mapped Diagnostic Context)
- Propagado em logs e traces

**Padrão de Log**
```
[correlationId=<uuid>] [AUTHORIZE] Processando autorização: terminalId=<id>, nsu=<nsu>
```

### Tracing Distribuído

**OpenTelemetry + Micrometer**
- Exportação de traces para sistemas de observabilidade
- Contexto propagado entre serviços (W3C Trace Context)

### Métricas

**Actuator**
- Health endpoint: `/actuator/health`
- Métricas Resilience4j: `/actuator/prometheus`
- Información sobre aplicação

---

## 8. Decisões Arquiteturais

### Por que 3 módulos (não 2, não 4+)?

#### ✅ Por que NÃO 2?
- `auth-commons` como library permite reutilização em futuros serviços
- Separação clara entre Security Context e Transaction Context

#### ✅ Por que NÃO 4+?
- DTOs são contextuais, não merecem módulo próprio
- `ExternalPaymentServiceImpl` é simples, não merece módulo
- `auth-gateway` como serviço HTTP seria overkill (adicionaria latência)

### auth-commons: JAR Library vs HTTP Service?

| Aspecto | JAR Library | HTTP Service |
|---------|-------------|--------------|
| Latência | Zero | ~100ms por request |
| Complexidade | Baixa | Alta |
| Reutilização | Direto (import) | Indireto (RestClient) |
| Escala | N/A | Sim |
| **Escolhido** | ✅ | Futuro |

**Razão**: Simples, fast-path, permite reutilização imediata. Evolui para HTTP gateway conforme escala.

### Banco de Dados: Compartilhado vs Separado?

| Abordagem | Benefício | Custo |
|-----------|-----------|-------|
| **Compartilhado** (escolhido) | Simplicidade, transações ACID locais | Eventual consolidation tech debt |
| Separado por serviço | Autonomia, escala independente | Eventual consistency, saga complexa |

**Razão**: `external-payment-mock` é stateless. Apenas `pos-transactions-api` acessa BD. Futuro: BDs separados conforme serviços crescem.

---

## 9. Evoluções Futuras

1. **Domain Events**: Adicionar `TransactionAuthorizedEvent`, `TransactionConfirmedEvent` para auditoria
2. **Event Sourcing**: Manter event log em vez de snapshots apenas
3. **Saga Pattern**: Para consistência eventual entre Transaction e Payment Contexts
4. **API Gateway**: Consolidar HMAC validation em Spring Cloud Gateway
5. **Service Mesh**: Istio/Linkerd para observabilidade, resiliência, segurança
6. **CQRS**: Separar read model (queries) de write model (commands) se volume crescer

---

## 10. Referências Externas

- **Domain-Driven Design**: Eric Evans, *Domain-Driven Design: Tackling Complexity in the Heart of Software*
- **Building Microservices**: Sam Newman, *Building Microservices: Designing Fine-Grained Systems*
- **Spring Boot Best Practices**: [spring.io](https://spring.io)
- **Resilience4j**: [resilience4j.readme.io](https://resilience4j.readme.io)
- **OpenTelemetry**: [opentelemetry.io](https://opentelemetry.io)

