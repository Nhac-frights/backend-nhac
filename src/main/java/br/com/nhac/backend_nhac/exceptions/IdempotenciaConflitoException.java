package br.com.nhac.backend_nhac.exceptions;

import org.springframework.http.HttpStatus;

public class IdempotenciaConflitoException extends NhacException {
    public IdempotenciaConflitoException(String mensagem) {
        super(mensagem, ErrorCode.IDEMPOTENCIA_CONFLITO);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.CONFLICT;
    }
}
