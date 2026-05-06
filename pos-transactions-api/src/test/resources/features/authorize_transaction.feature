Feature: Autorizar Transação POS
  Como um terminal POS
  Quero autorizar transações de pagamento
  Para que os pagamentos sejam processados com segurança

  Scenario: Autorizar uma nova transação com sucesso
    Given que não existe transação para o terminal "T-1000" com NSU "123456"
    When eu autorizo uma transação com terminalId "T-1000", NSU "123456" e valor 199.90
    Then a resposta deve ter status 200
    And a resposta deve conter um transactionId
    And a transação deve estar com status AUTHORIZED no banco de dados

  Scenario: Requisição idempotente de autorização deve retornar a mesma transação
    Given existe uma transação autorizada com transactionId "TXN-IDEM-001"
    When eu autorizo novamente a mesma transação com terminalId "T-BDD", NSU "BDD-NSU-001" e valor 100.00
    Then a resposta deve ter status 200
    And a resposta deve conter o mesmo transactionId "TXN-IDEM-001"
