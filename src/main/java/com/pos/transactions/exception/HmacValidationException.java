package com.pos.transactions.exception;

/**
 * @deprecated Esta exceção não é mais utilizada.
 *
 * <p>O {@code HmacSignatureFilter} escreve a resposta HTTP 401 diretamente no
 * {@code HttpServletResponse} (antes do DispatcherServlet), portanto o
 * {@code GlobalExceptionHandler} nunca é invocado para erros de assinatura HMAC.
 *
 * <p>Mantida apenas para compatibilidade de compilação até remoção física do arquivo.
 * Não lançar nem referenciar esta exceção em código novo.
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public class HmacValidationException extends RuntimeException {

    public HmacValidationException(String message) {
        super(message);
    }
}
