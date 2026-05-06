Feature: Confirmar Transação POS
  Como um terminal POS
  Quero confirmar transações autorizadas
  Para finalizar o pagamento

  Scenario: Confirmar uma transação autorizada com sucesso
    Given existe uma transação autorizada com transactionId "TXN-CONFIRM-001"
    When eu confirmo a transação com transactionId "TXN-CONFIRM-001"
    Then a resposta deve ter status 204
    And a transação deve estar com status CONFIRMED no banco de dados

  Scenario: Confirmar uma transação já confirmada deve ser idempotente
    Given existe uma transação autorizada com transactionId "TXN-CONFIRM-002"
    When eu confirmo a transação com transactionId "TXN-CONFIRM-002"
    And eu confirmo a transação com transactionId "TXN-CONFIRM-002"
    Then a resposta deve ter status 204
