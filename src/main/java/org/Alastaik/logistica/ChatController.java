package org.Alastaik.logistica;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final LogisticaService logisticaService;

    public ChatController(LogisticaService logisticaService) {
        this.logisticaService = logisticaService;
    }

    /**
     * Endpoint para processar mensagens com IA
     * POST /api/chat/message
     * 
     * Body: {
     * "userId": "...",
     * "message": "...",
     * "history": "..." (Opcional)
     * }
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> processarMensagem(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String userName = request.get("userName"); // NOVO: nome do contato
        String message = request.get("message");
        String history = request.get("history"); // Histórico vindo do Node.js

        System.out.println("🤖 Processando mensagem de " + userName + " (" + userId + "): " + message);

        if (userId == null || message == null) {
            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Campos 'userId' e 'message' são obrigatórios");
            return ResponseEntity.badRequest().body(erro);
        }

        try {
            // Usa userName se disponível, senão usa userId
            String nomeUsuario = (userName != null && !userName.isBlank()) ? userName : userId;

            // Processa com IA (passando histórico e nome)
            String resposta = logisticaService.processarMensagem(userId, nomeUsuario, message, history);

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", true);
            resultado.put("reply", resposta);

            System.out.println("✅ Resposta gerada: " + resposta);
            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            System.err.println("❌ Erro ao processar mensagem: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> erro = new HashMap<>();
            erro.put("success", false);
            erro.put("message", "Erro ao processar: " + e.getMessage());
            return ResponseEntity.status(500).body(erro);
        }
    }

    /**
     * Endpoint para consultar métricas do sistema
     * GET /api/chat/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("metrics", logisticaService.getMetrics());
        return ResponseEntity.ok(response);
    }
}
