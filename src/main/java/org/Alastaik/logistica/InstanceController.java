package org.Alastaik.logistica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.whatsapp.api-url:http://whatsapp-service:3000}")
    private String WHATSAPP_API_URL;

    public InstanceController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/criar")
    public ResponseEntity<Map<String, Object>> criarInstancia() {
        return proxyRequest("/session/start", HttpMethod.POST, "Iniciando sessão...");
    }

    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> obterQRCode() {
        return proxyRequest("/session/qr", HttpMethod.GET, "Obtendo QR Code...");
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> verificarStatus() {
        return proxyRequest("/session/status", HttpMethod.GET, "Verificando status...");
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Map<String, Object>> desconectar() {
        return proxyRequest("/session/logout", HttpMethod.DELETE, "Desconectando...");
    }

    private ResponseEntity<Map<String, Object>> proxyRequest(String endpoint, HttpMethod method, String logMsg) {
        try {
            String url = WHATSAPP_API_URL + endpoint;
            System.out.println("🔧 " + logMsg + " (" + url + ")");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            Map<String, Object> resultado = new HashMap<>();
            if (responseJson.has("success")) resultado.put("success", responseJson.get("success").asBoolean());
            if (responseJson.has("message")) resultado.put("message", responseJson.get("message").asText());
            if (responseJson.has("status")) resultado.put("status", responseJson.get("status").asText());
            if (responseJson.has("qrcode")) resultado.put("qrcode", responseJson.get("qrcode").asText());
            
            // Campos extras de status
            if (responseJson.has("isReady")) resultado.put("isReady", responseJson.get("isReady").asBoolean());
            if (responseJson.has("hasQrCode")) resultado.put("hasQrCode", responseJson.get("hasQrCode").asBoolean());

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            System.err.println("❌ Erro em " + endpoint + ": " + e.getMessage());
            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Erro: " + e.getMessage());
            return ResponseEntity.status(500).body(erro);
        }
    }
}
