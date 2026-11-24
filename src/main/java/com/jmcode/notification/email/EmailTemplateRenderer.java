package com.jmcode.notification.email;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sustituye placeholders {@code {{clave}}} y decide si el cuerpo es HTML.
 * Aislado del canal para poder testearlo sin SMTP ni contexto de Spring.
 */
@Component
public class EmailTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    /** Detecta una etiqueta HTML de apertura/cierre o un doctype. */
    private static final Pattern HTML_MARKUP = Pattern.compile("<\\s*(!doctype\\b|/?[a-z][a-z0-9]*\\b[^>]*)>",
            Pattern.CASE_INSENSITIVE);

    public String render(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty() || variables == null || variables.isEmpty()) {
            return template;
        }
        return PLACEHOLDER.matcher(template).replaceAll(match -> {
            String value = variables.get(match.group(1));
            // Un placeholder sin valor se deja intacto en lugar de convertirse en "null".
            return value == null
                    ? java.util.regex.Matcher.quoteReplacement(match.group())
                    : java.util.regex.Matcher.quoteReplacement(value);
        });
    }

    public boolean looksLikeHtml(String content) {
        return content != null && !content.isEmpty() && HTML_MARKUP.matcher(content).find();
    }
}
