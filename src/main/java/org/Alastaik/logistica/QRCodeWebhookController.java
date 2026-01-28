package org.Alastaik.logistica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/webhook")
public class QRCodeWebhookController {

    private final ObjectMapper objectMapper;

    // Armazena o último QR Code recebido em memória
    private final Map<String, String> qrCodeCache = new ConcurrentHashMap<>();

    public QRCodeWebhookController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Webhook que recebe eventos da Evolution API
     * POST /api/webhook/evolution
     */
    @PostMapping("/evolution")
    public void receberEventoEvolution(@RequestBody String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            // Log de todos os eventos para debug
            System.out.println("📥 Webhook recebido: " + root.path("event").asText());

            // Verifica se é evento de QR Code
            String event = root.path("event").asText();
            if ("qrcode.updated".equals(event) || "QRCODE_UPDATED".equals(event)) {
                JsonNode data = root.path("data");
                String instance = root.path("instance").asText();

                // Extrai o QR Code - pode estar em diferentes formatos
                String qrCode = null;

                if (data.has("qrcode")) {
                    JsonNode qrNode = data.get("qrcode");
                    if (qrNode.has("base64")) {
                        qrCode = qrNode.get("base64").asText();
                    } else if (qrNode.isTextual()) {
                        qrCode = qrNode.asText();
                    }
                } else if (data.has("base64")) {
                    qrCode = data.get("base64").asText();
                } else if (data.isTextual()) {
                    qrCode = data.asText();
                }

                if (qrCode != null && !qrCode.isEmpty()) {
                    // Garante que tem o prefixo data:image
                    if (!qrCode.startsWith("data:image")) {
                        qrCode = "data:image/png;base64," + qrCode;
                    }

                    qrCodeCache.put(instance, qrCode);
                    System.out.println("✅ QR Code capturado via webhook para instância: " + instance);
                    System.out.println("📱 QR Code salvo (primeiros 50 chars): "
                            + qrCode.substring(0, Math.min(50, qrCode.length())));
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao processar webhook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Retorna o último QR Code capturado
     * GET /api/webhook/qrcode/{instanceName}
     */
    @GetMapping("/qrcode/{instanceName}")
    public Map<String, Object> obterQRCodeCapturado(@PathVariable String instanceName) {
        Map<String, Object> response = new HashMap<>();

        String qrCode = qrCodeCache.get(instanceName);

        if (qrCode != null) {
            response.put("success", true);
            response.put("qrcode", qrCode);
            response.put("instance", instanceName);
            System.out.println("📤 QR Code retornado para: " + instanceName);
        } else {
            response.put("success", false);
            response.put("message", "QR Code não encontrado. Aguarde o webhook receber o evento.");
            response.put("hint", "Tente criar a instância novamente ou aguarde alguns segundos.");
        }

        return response;
    }

    /**
     * Lista todas as instâncias com QR Code
     * GET /api/webhook/qrcodes
     */
    @GetMapping("/qrcodes")
    public Map<String, Object> listarQRCodes() {
        Map<String, Object> response = new HashMap<>();
        response.put("count", qrCodeCache.size());
        response.put("instances", qrCodeCache.keySet());
        return response;
    }
}
