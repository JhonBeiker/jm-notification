package com.jmcode.notification.common;

/**
 * Un proveedor externo respondió con error o no respondió. Se traduce a 502: el fallo no es
 * de esta API ni de la petición del cliente, así que no puede confundirse con un 400 ni con
 * un 500 propio.
 *
 * <p>{@code status} es el código que devolvió el proveedor, o {@code 0} si ni siquiera se
 * pudo hablar con él. Sirve para que quien llama distinga los casos que sí sabe tolerar
 * (p. ej. un 409 que en realidad significa "ya estaba hecho").
 */
public class UpstreamServiceException extends RuntimeException {

    private final String provider;
    private final int status;

    public UpstreamServiceException(String provider, int status, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatus() {
        return status;
    }
}
