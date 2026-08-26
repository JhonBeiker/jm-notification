package com.jmcode.notification.common;

/**
 * Credenciales de administrador inválidas. Se mapea a 401 sin distinguir entre
 * "usuario inexistente" y "password incorrecta", para no filtrar qué emails existen.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
