Feature: Desfazer Transação POS
  Como um terminal POS
  Quero desfazer transações autorizadas
  Para cancelar pagamentos quando necessário

  Scenario: Desfazer uma transação por transactionId com sucesso
    Given existe uma transação autorizada com transactionId "TXN-VOID-001"
    When eu desfaço a transação com transactionId "TXN-VOID-001"
    Then a resposta deve ter status 204
    And a transação deve estar com status VOIDED no banco de dados

  Scenario: Desfazer uma transação já desfeita deve ser idempotente
    Given existe uma transação autorizada com transactionId "TXN-VOID-002"
    When eu desfaço a transação com transactionId "TXN-VOID-002"
    And eu desfaço a transação com transactionId "TXN-VOID-002"
    Then a resposta deve ter status 204
