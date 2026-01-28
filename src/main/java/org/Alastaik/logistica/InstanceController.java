package org.Alastaik.logistica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/instance")
public class InstanceController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Configurações da API WhatsApp (agora whatsapp-service)
    private static final String WHATSAPP_API_URL = "http://localhost:3000";
    private static final String SESSION_NAME = "LogisticaBot";

    public InstanceController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * ENDPOINT 1: Criar/Iniciar Sessão WhatsApp
     * POST /api/instance/criar
     */
    @PostMapping("/criar")
    public ResponseEntity<Map<String, Object>> criarInstancia() {
        try {
            String url = WHATSAPP_API_URL + "/session/start";

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Faz a requisição
            System.out.println("🔧 Iniciando sessão WhatsApp...");
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            // Parse da resposta
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", responseJson.get("success").asBoolean());
            resultado.put("message", responseJson.get("message").asText());
            resultado.put("status", responseJson.get("status").asText());

            System.out.println("✅ Sessão iniciada");
            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            System.err.println("❌ Erro ao iniciar sessão: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Erro ao iniciar sessão: " + e.getMessage());
            return ResponseEntity.status(500).body(erro);
        }
    }

    /**
     * ENDPOINT 2: Obter QR Code
     * GET /api/instance/qrcode
     */
    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> obterQRCode() {
        try {
            String url = WHATSAPP_API_URL + "/session/qr";

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Faz a requisição
            System.out.println("📱 Obtendo QR Code...");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            // Parse da resposta
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", responseJson.get("success").asBoolean());

            if (responseJson.has("qrcode")) {
                resultado.put("qrcode", responseJson.get("qrcode").asText());
                System.out.println("✅ QR Code obtido");
            } else {
                resultado.put("message", responseJson.get("message").asText());
            }

            resultado.put("status", responseJson.get("status").asText());

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            System.err.println("❌ Erro ao obter QR Code: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Erro ao obter QR Code: " + e.getMessage());
            return ResponseEntity.status(500).body(erro);
        }
    }

    /**
     * ENDPOINT 3: Verificar Status da Conexão
     * GET /api/instance/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> verificarStatus() {
        try {
            String url = WHATSAPP_API_URL + "/session/status";

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Faz a requisição
            System.out.println("🔍 Verificando status da sessão WhatsApp");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            // Parse da resposta
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", responseJson.get("success").asBoolean());
            resultado.put("status", responseJson.get("status").asText());
            resultado.put("isReady", responseJson.get("isReady").asBoolean());
            resultado.put("hasQrCode", responseJson.get("hasQrCode").asBoolean());

            System.out.println("✅ Status: " + responseJson.get("status").asText());
            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            System.err.println("❌ Erro ao verificar status: " + e.getMessage());

            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Erro ao verificar status: " + e.getMessage());
            return ResponseEntity.status(500).body(erro);
        }
    }

    /**
     * ENDPOINT 4: Desconectar (Logout)
     * DELETE /api/instance/logout
     */
    @DeleteMapping("/logout")
    public ResponseEntity<Map<String, Object>> desconectar() {
        try {
            String url = WHATSAPP_API_URL + "/session/logout";

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            // Faz a requisição
            System.out.println("🔌 Desconectando sessão WhatsApp");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);

            // Parse da resposta
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", responseJson.get("success").asBoolean());
            resultado.put("message", responseJson.get("message").asText());

            System.out.println("✅ Sessão desconectada");
            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            System.err.println("❌ Erro ao desconectar: " + e.getMessage());

            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Erro ao desconectar: " + e.getMessage());
            return ResponseEntity.status(500).body(erro);
        }
    }
}
