package com.jmcode.notification.channel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRequestTest {

    private static NotificationRequest request(String clientCode, Map<String, String> metadata) {
        return new NotificationRequest(ChannelType.EMAIL, "to@example.com", "asunto", "cuerpo",
                clientCode, metadata, null, null, null);
    }

    @Test
    void prefersTheExplicitClientCode() {
        assertEquals("cliente-a", request("cliente-a", Map.of("clientCode", "cliente-b")).effectiveClientCode());
    }

    @Test
    void fallsBackToMetadata() {
        assertEquals("cliente-b", request(null, Map.of("clientCode", "cliente-b")).effectiveClientCode());
    }

    @Test
    void returnsNullWhenNoClientCodeIsGiven() {
        assertNull(request(null, Map.of()).effectiveClientCode());
        assertNull(request("  ", null).effectiveClientCode());
        assertNull(request(null, Map.of("clientCode", " ")).effectiveClientCode());
    }

    @Test
    void trimsTheResolvedClientCode() {
        assertEquals("cliente-a", request("  cliente-a  ", null).effectiveClientCode());
        assertEquals("cliente-b", request(null, Map.of("clientCode", " cliente-b ")).effectiveClientCode());
    }

    @Test
    void nullCollectionsBecomeEmptySoCallersNeverCheckForNull() {
        NotificationRequest request = request(null, null);

        assertTrue(request.metadata().isEmpty());
        assertTrue(request.variables().isEmpty());
        assertTrue(request.attachments().isEmpty());
        assertFalse(request.hasAttachments());
        assertFalse(request.hasTemplate());
    }

    @Test
    void detectsTemplatesAndAttachments() {
        NotificationRequest request = new NotificationRequest(ChannelType.EMAIL, "to@example.com", null, "cuerpo",
                null, null, "factura", Map.of("n", "1"),
                List.of(new Attachment("f.pdf", "application/pdf", "AAAA")));

        assertTrue(request.hasTemplate());
        assertTrue(request.hasAttachments());
    }
}
