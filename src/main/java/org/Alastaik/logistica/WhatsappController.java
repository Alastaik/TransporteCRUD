package org.Alastaik.logistica;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {

    @Value("${app.whatsapp.api-url:http://whatsapp-service:3000}")
    private String whatsappApiUrl;

    private final RestTemplate restTemplate;

    private final LogisticaService logisticaService;

    public WhatsappController(LogisticaService logisticaService) {
        this.logisticaService = logisticaService;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    // --- CONTROLE DO BOT ---
    @GetMapping("/bot-status")
    public ResponseEntity<Map<String, Boolean>> getBotStatus() {
        return ResponseEntity.ok(Map.of("active", logisticaService.isBotAtivo()));
    }

    @PostMapping("/bot-toggle")
    public ResponseEntity<Map<String, Boolean>> toggleBot() {
        boolean novoStatus = !logisticaService.isBotAtivo();
        logisticaService.setBotAtivo(novoStatus);
        return ResponseEntity.ok(Map.of("active", novoStatus));
    }

    // --- PROXY WHATSAPP ---
    @PostMapping("/start")
    public ResponseEntity<?> startSession() {
        return proxyRequest("/session/start", HttpMethod.POST);
    }

    @GetMapping("/qr")
    public ResponseEntity<?> getQrCode() {
        return proxyRequest("/session/qr", HttpMethod.GET);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return proxyRequest("/session/status", HttpMethod.GET);
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logout() {
        return proxyRequest("/session/logout", HttpMethod.DELETE);
    }

    private ResponseEntity<?> proxyRequest(String path, HttpMethod method) {
        String url = whatsappApiUrl + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            return restTemplate.exchange(url, method, entity, Map.class);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
