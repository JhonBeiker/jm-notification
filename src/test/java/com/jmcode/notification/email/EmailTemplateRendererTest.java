package com.jmcode.notification.email;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void replacesPlaceholders() {
        String result = renderer.render("Hola {{nombre}}, tu pedido {{pedido}} salió",
                Map.of("nombre", "Ana", "pedido", "1234"));

        assertEquals("Hola Ana, tu pedido 1234 salió", result);
    }

    @Test
    void toleratesSpacesInsideThePlaceholder() {
        assertEquals("Hola Ana", renderer.render("Hola {{ nombre }}", Map.of("nombre", "Ana")));
    }

    @Test
    void leavesUnknownPlaceholdersUntouchedInsteadOfWritingNull() {
        assertEquals("Hola {{nombre}}", renderer.render("Hola {{nombre}}", Map.of("otro", "x")));
    }

    @Test
    void treatsValuesAsLiteralText() {
        String result = renderer.render("Total: {{importe}}", Map.of("importe", "$100"));

        assertEquals("Total: $100", result);
    }

    @Test
    void returnsTemplateUnchangedWhenThereAreNoVariables() {
        assertEquals("Sin variables", renderer.render("Sin variables", Map.of()));
        assertEquals("Sin variables", renderer.render("Sin variables", null));
    }

    @Test
    void detectsHtmlBodies() {
        assertTrue(renderer.looksLikeHtml("<p>Hola</p>"));
        assertTrue(renderer.looksLikeHtml("<!doctype html><html></html>"));
        assertTrue(renderer.looksLikeHtml("Texto con <br> salto"));
        assertTrue(renderer.looksLikeHtml("<a href=\"https://x\">link</a>"));
    }

    @Test
    void plainTextIsNotTreatedAsHtml() {
        assertFalse(renderer.looksLikeHtml("Hola, tu pedido salió"));
        assertFalse(renderer.looksLikeHtml("2 < 3 y 5 > 4"));
        assertFalse(renderer.looksLikeHtml(""));
        assertFalse(renderer.looksLikeHtml(null));
    }
}
