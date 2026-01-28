package org.Alastaik.logistica;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.Alastaik.logistica.model.OrdemServico;
import org.Alastaik.logistica.repository.OsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Map;

@Service
public class LogisticaService {

    private boolean botAtivo = true; // Bot começa ativado

    public boolean isBotAtivo() {
        return botAtivo;
    }

    public void setBotAtivo(boolean ativo) {
        this.botAtivo = ativo;
    }

    private final OsRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // API Key do Groq (injetada do application.properties)
    @Value("${groq.api-key:}")
    private String groqApiKey;

    // Memória de backup (caso não venha histórico do front)
    private final ConcurrentHashMap<String, List<String>> memoriaBackup = new ConcurrentHashMap<>();

    // Controle de concorrência: máximo 2 requisições simultâneas à IA
    private static final int MAX_CONCURRENT_AI_REQUESTS = 2;
    private final Semaphore aiSemaphore = new Semaphore(MAX_CONCURRENT_AI_REQUESTS, true);
    private static final int AI_TIMEOUT_SECONDS = 60;

    // ═══════════════════════════════════════════════════════════
    // 📊 MÉTRICAS E CONTADORES
    // ═══════════════════════════════════════════════════════════
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong ignoredRequests = new AtomicLong(0);

    // Limites de passageiros por veículo
    private static final Map<String, Integer> LIMITE_PASSAGEIROS = Map.of(
            "Carro Convencional", 4,
            "Caminhonete", 3,
            "Van", 15);

    public LogisticaService(OsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Chama a API do Groq (formato OpenAI-compatible)
     * Com fallback automático para modelo alternativo se atingir limite
     */
    private String chamarGroq(String systemPrompt, String userMessage) throws Exception {
        // Modelo primário e fallback
        String primaryModel = "qwen/qwen3-32b";
        String fallbackModel = "llama-3.3-70b-versatile";

        // Tenta modelo primário primeiro
        try {
            System.out.println("🤖 Tentando Primário (" + primaryModel + ")...");
            return chamarGroqComModelo(systemPrompt, userMessage, primaryModel);
        } catch (Exception e) {
            // FALHA NO PRIMÁRIO: Loga e tenta o secundário
            System.err.println("⚠️ Primário falhou: " + e.getMessage() + ". Acionando Fallback...");

            try {
                // TENTATIVA 2: O Especialista
                System.out.println("🚑 Tentando Fallback (" + fallbackModel + ")...");
                return chamarGroqComModelo(systemPrompt, userMessage, fallbackModel);
            } catch (Exception exFallback) {
                // CAOS TOTAL: Ambos falharam
                throw new RuntimeException("❌ Ambos os modelos falharam. Serviço indisponível.", exFallback);
            }
        }
    }

    /**
     * Chamada HTTP para Groq com modelo específico
     */
    private String chamarGroqComModelo(String systemPrompt, String userMessage, String modelName) throws Exception {
        String url = "https://api.groq.com/openai/v1/chat/completions";

        // Monta o body no formato OpenAI
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)),
                "temperature", 0.0,
                "response_format", Map.of("type", "json_object") // Força JSON!
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + groqApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Detecta limite de tokens/rate limit (429 ou mensagem de erro específica)
        if (response.statusCode() == 429 ||
                (response.statusCode() != 200 && response.body().contains("rate_limit"))) {
            throw new GroqRateLimitException("Rate limit atingido para modelo: " + modelName);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.statusCode() + " - " + response.body());
        }

        System.out.println("🤖 Modelo usado: " + modelName);

        // Extrai o texto da resposta (formato OpenAI)
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }

        throw new RuntimeException("Resposta do Groq sem conteúdo válido");
    }

    /**
     * Exceção personalizada para limite de rate do Groq
     */
    private static class GroqRateLimitException extends Exception {
        public GroqRateLimitException(String message) {
            super(message);
        }
    }

    /**
     * Retorna métricas do sistema para monitoramento
     */
    public Map<String, Object> getMetrics() {
        long total = totalRequests.get();
        long success = successfulRequests.get();
        long failed = failedRequests.get();
        long ignored = ignoredRequests.get();
        double errorRate = total > 0 ? (double) failed / total * 100 : 0;

        return Map.of(
                "totalRequests", total,
                "successfulRequests", success,
                "failedRequests", failed,
                "ignoredRequests", ignored,
                "errorRate", String.format("%.2f%%", errorRate),
                "concurrentSlots", aiSemaphore.availablePermits() + "/" + MAX_CONCURRENT_AI_REQUESTS);
    }

    public String processarMensagem(String userId, String nomeUsuario, String mensagemUsuario, String historyContext) {
        if (!botAtivo) {
            System.out.println("⛔ Bot desativado. Ignorando mensagem de: " + nomeUsuario);
            return ""; // Retorna string vazia para o Node.js não responder nada
        }

        // 1. Definição do Contexto
        String contextoConversa;

        if (historyContext != null && !historyContext.isBlank()) {
            contextoConversa = historyContext;
        } else {
            // Fallback
            List<String> hist = memoriaBackup.getOrDefault(userId, new ArrayList<>());
            hist.add("User: " + mensagemUsuario);
            memoriaBackup.put(userId, hist);
            contextoConversa = String.join("\n", hist);
        }

        // 2. Usa o nome do usuário passado como parâmetro (vem do WhatsApp)

        // 3. Prompt Ultra-Compacto (Otimizado para reduzir tokens)
        var zoneId = java.time.ZoneId.of("America/Sao_Paulo");
        var agora = java.time.ZonedDateTime.now(zoneId);
        var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String dataHoraAtual = agora.format(formatter);

        String systemPrompt = String.format(
                """
                        ROLE: API Logística TJGO.
                        AGORA: %s
                        USER_REF: %s

                        TASK:
                        1. Analise a ÚLTIMA mensagem.
                        2. VERIFIQUE O HISTÓRICO: Se a mensagem for uma continuação/resposta (ex: "Sim", "Eu", "Às 14h"), VOCÊ DEVE RECUPERAR os dados (Destino, Data) das mensagens anteriores. Mantenha o contexto.

                        SCOPE FILTER (CRITICAL):
                        - Saudações ISOLADAS ("Oi") -> STATUS="IGNORE".
                        - Saudação + PEDIDO -> PROCESSAR.
                        - Assunto Aleatório -> STATUS="IGNORE".

                        LOGIC RULES (STRICT MODE):
                        - VEICULO: Default="Carro Convencional".
                          * Se não citado -> Mantenha Default (NÃO PERGUNTE).
                          * Pediu Moto/Bus -> STATUS=INCOMPLETE.

                        - DESTINO: Formato "Local - Cidade".
                          * Default Cidade="Goiânia".
                          * Ex: "Fórum" -> "Fórum - Goiânia".

                        - PROAD:
                          * Cidade="Goiânia" (ou "TJGO") -> PROAD=null.
                          * Cidade!="Goiânia" -> PROAD=OBRIGATÓRIO (String).
                          * OBS: "Aparecida de Goiânia" EXIGE PROAD.

                        - RETORNO (CRITICAL):
                          * OBRIGATÓRIO EXPLÍCITO (true/false).
                          * Omitido -> STATUS=INCOMPLETE.
                          * PROIBIDO INFERIR "FALSE".
                          * User deve dizer: "só ida", "não espera", "volto", "aguardar".

                        - PAX:
                          * "eu" -> ["USER_REF_PLACEHOLDER"].
                          * Omitido ou "nós" sem nomes -> STATUS=INCOMPLETE.

                        - PERSISTENCIA:
                          * Se o dado já existe no histórico, MANTENHA. Não zere.

                        STATUS DEF:
                        - INCOMPLETE: Faltam dados.
                        - COMPLETED: Tudo Válido.
                        - IGNORE: Fora do escopo.

                        RESPONSE FORMAT (JSON ONLY):
                        {"raciocinio":"...","status":"...","dados":{"nome_solicitante":"string","destino":"string","data_hora_iso":"iso8601","passageiros":["string"],"aguardar_retorno":bool,"proad":"string","tipo_veiculo":"string"},"mensagem_usuario":"string"}

                        EXAMPLES:
                        User: "Bom dia"
                        JSON: {"status":"IGNORE","dados":null,"mensagem_usuario":null}

                        User: "Boa tarde, quero um veículo"
                        JSON: {"raciocinio":"Intenção clara, faltam dados","status":"INCOMPLETE","dados":{"nome_solicitante":"SOLICITANTE_EXEMPLO","destino":null,"data_hora_iso":null,"passageiros":null,"aguardar_retorno":null,"proad":null,"tipo_veiculo":"Carro Convencional"},"mensagem_usuario":"Para onde, qual dia/horário e quem vai?"}

                        User: "Van p/ Anápolis amanhã 8h, eu vou, PROAD 99, só ida"
                        JSON: {"raciocinio":"Ok. Anápolis+PROAD.","status":"COMPLETED","dados":{"nome_solicitante":"SOLICITANTE_EXEMPLO","destino":"Anápolis","data_hora_iso":"2026-01-28T08:00:00-03:00","passageiros":["SOLICITANTE_EXEMPLO"],"aguardar_retorno":false,"proad":"99","tipo_veiculo":"Van"},"mensagem_usuario":"✅ Agendado Van para Anápolis."}
                        """,
                dataHoraAtual, nomeUsuario);

        String respostaIA = null;
        String providerUsed = "OpenRouter (Primary)";

        // Controle de concorrência: aguarda slot disponível (máx 2 simultâneas)
        boolean acquired = false;
        try {
            int waitingInQueue = MAX_CONCURRENT_AI_REQUESTS - aiSemaphore.availablePermits();
            if (waitingInQueue > 0) {
                System.out.println(
                        "⏳ Requisição de " + nomeUsuario + " aguardando na fila (" + waitingInQueue + " na frente)");
            }

            acquired = aiSemaphore.tryAcquire(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                System.err.println(
                        "⏱️ Timeout: requisição de " + nomeUsuario + " excedeu " + AI_TIMEOUT_SECONDS + "s na fila");
                return null; // Timeout - não responde
            }

            System.out.println("🚀 Processando requisição de " + nomeUsuario + " (slots livres: "
                    + aiSemaphore.availablePermits() + "/" + MAX_CONCURRENT_AI_REQUESTS + ")");

            // Chama API do Groq com JSON forçado
            respostaIA = chamarGroq(systemPrompt, contextoConversa);
            providerUsed = "Groq (Qwen 3 32B)";

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            System.err.println("❌ Requisição interrompida: " + ie.getMessage());
            return null;
        } catch (Exception primaryException) {
            System.err.println("❌ Gemini falhou: " + primaryException.getMessage());
            return null;
        } finally {
            if (acquired) {
                aiSemaphore.release();
            }
        }

        try {
            System.out.println("🤖 Provider usado: " + providerUsed);

            if (respostaIA == null || respostaIA.isBlank()) {
                System.err.println("❌ OpenRouter retornou resposta vazia/null!");
                System.err.println("📤 System Prompt (primeiras 500 chars): "
                        + systemPrompt.substring(0, Math.min(500, systemPrompt.length())));
                System.err.println("📤 User Context (primeiras 200 chars): "
                        + contextoConversa.substring(0, Math.min(200, contextoConversa.length())));
                return null;
            }

            System.out.println("🤖 IA Raw: " + respostaIA);

            String jsonLimpo = limparJson(respostaIA);

            if (jsonLimpo == null || jsonLimpo.isBlank()) {
                System.err.println("❌ JSON limpo está vazio!");
                return null;
            }

            LogisticsResponse response = objectMapper.readValue(jsonLimpo, LogisticsResponse.class);

            // Incrementa contador total
            totalRequests.incrementAndGet();

            // 3. Máquina de Estados
            switch (response.status()) {
                case IGNORE -> {
                    ignoredRequests.incrementAndGet();
                    System.out.println("🤫 Interação ignorada: " + response.raciocinio());
                    return response.mensagemUsuario() != null ? response.mensagemUsuario() : "";
                }
                case INCOMPLETE -> {
                    atualizarBackup(userId, response.mensagemUsuario());
                    return response.mensagemUsuario();
                }
                case COMPLETED -> {
                    // Validação extra: se status COMPLETED mas dados null, volta para INCOMPLETE
                    if (response.dados() == null) {
                        System.err.println("⚠️ Status COMPLETED mas 'dados' veio null!");
                        failedRequests.incrementAndGet();
                        return response.mensagemUsuario() != null
                                ? response.mensagemUsuario()
                                : null;
                    }

                    // ═══════════════════════════════════════════════════════════
                    // � JAVA-FIRST: Aplicar defaults (economiza tokens de output)
                    // ═══════════════════════════════════════════════════════════
                    String tipoVeiculoFinal = response.dados().tipoVeiculo();
                    if (tipoVeiculoFinal == null || tipoVeiculoFinal.isBlank()) {
                        tipoVeiculoFinal = "Carro Convencional";
                        System.out.println("🚗 tipoVeiculo null -> Default: Carro Convencional");
                    }

                    // ═══════════════════════════════════════════════════════════
                    // �🕐 VALIDAÇÃO 1: Data no passado
                    // ═══════════════════════════════════════════════════════════
                    if (response.dados().dataHoraIso() != null) {
                        try {
                            ZonedDateTime dataViagem = ZonedDateTime.parse(response.dados().dataHoraIso());
                            ZonedDateTime agoraValidacao = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
                            if (dataViagem.isBefore(agoraValidacao)) {
                                return "❌ A data/hora informada já passou. Por favor, informe uma data futura.";
                            }
                        } catch (Exception e) {
                            System.err.println("⚠️ Erro ao validar data: " + e.getMessage());
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // 👥 VALIDAÇÃO 2: Limite de passageiros por veículo
                    // ═══════════════════════════════════════════════════════════
                    if (response.dados().passageiros() != null) {
                        int qtdPassageiros = response.dados().passageiros().size();
                        int limite = LIMITE_PASSAGEIROS.getOrDefault(tipoVeiculoFinal, 4);
                        if (qtdPassageiros > limite) {
                            return "❌ O veículo '" + tipoVeiculoFinal +
                                    "' comporta no máximo " + limite + " passageiros. " +
                                    "Você informou " + qtdPassageiros + ". Deseja uma Van?";
                        }
                    }

                    if (response.dados().aguardarRetorno() == null) {
                        return "Desculpe, preciso confirmar: o motorista deve aguardar o retorno?";
                    }

                    // Validação de PROAD Ajustada
                    if (precisaProad(response.dados().destino()) &&
                            (response.dados().proad() == null || response.dados().proad().isBlank())) {
                        return "Para o destino '" + response.dados().destino()
                                + "', o número do PROAD é obrigatório. Por favor, informe.";
                    }

                    salvarOrdemServico(userId, response.dados(), tipoVeiculoFinal);
                    memoriaBackup.remove(userId);
                    successfulRequests.incrementAndGet();
                    return response.mensagemUsuario();
                }
                default -> {
                    return null;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Erro ao processar resposta IA: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String processarMensagem(String userId, String mensagemUsuario) {
        return processarMensagem(userId, userId, mensagemUsuario, null);
    }

    private String limparJson(String texto) {
        if (texto == null)
            return "{}";
        return texto.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    private void atualizarBackup(String userId, String mensagemBot) {
        List<String> hist = memoriaBackup.getOrDefault(userId, new ArrayList<>());
        hist.add("Bot: " + mensagemBot);
        memoriaBackup.put(userId, hist);
    }

    private boolean precisaProad(String destino) {
        if (destino == null)
            return false;
        String destinoBaixo = destino.toLowerCase();
        return !destinoBaixo.contains("goiânia") && !destinoBaixo.contains("goiania");
    }

    /**
     * Normaliza o destino para consistência no Power BI
     * - Corrige acentos
     * - Remove redundâncias ("Anápolis - Anápolis" -> "Anápolis")
     * - Padroniza nomes de cidades
     */
    private String normalizarDestino(String destino) {
        if (destino == null || destino.isBlank())
            return destino;

        String resultado = destino.trim();

        // 1. Corrige acentos em cidades comuns
        resultado = resultado
                .replaceAll("(?i)goiania", "Goiânia")
                .replaceAll("(?i)anapolis", "Anápolis")
                .replaceAll("(?i)trindade", "Trindade")
                .replaceAll("(?i)senador canedo", "Senador Canedo");

        // 2. Normaliza "Aparecida" para nome completo
        if (resultado.toLowerCase().contains("aparecida") &&
                !resultado.toLowerCase().contains("aparecida de goiânia")) {
            resultado = resultado.replaceAll("(?i)aparecida(?! de)", "Aparecida de Goiânia");
        }

        // 3. Remove redundâncias ("Cidade - Cidade" -> "Cidade")
        String[] partes = resultado.split(" - ");
        if (partes.length == 2) {
            String local = partes[0].trim();
            String cidade = partes[1].trim();
            // Se local e cidade são iguais (ignorando case), mantém só um
            if (local.equalsIgnoreCase(cidade)) {
                resultado = cidade; // Usa a cidade (provavelmente com formato correto)
            }
        }

        return resultado;
    }

    private void salvarOrdemServico(String userId, LogisticsData data, String tipoVeiculoFinal) {
        System.out.println(">>> SALVANDO OS (COMPLETED): " + data);

        OrdemServico os = new OrdemServico();
        os.setNumeroWhatsapp(userId);
        os.setNomeSolicitante(data.nomeSolicitante());
        os.setDestino(normalizarDestino(data.destino())); // Normaliza para Power BI
        os.setPassageiros(String.join(", ", data.passageiros()));
        os.setAguardarRetorno(data.aguardarRetorno());
        os.setProad(data.proad());
        os.setTipoVeiculo(tipoVeiculoFinal); // Usa o valor processado pelo Java

        try {
            var zonedDateTime = java.time.ZonedDateTime.parse(data.dataHoraIso());
            os.setDataHoraSaida(zonedDateTime.toLocalDateTime());
        } catch (Exception e) {
            System.err.println("Erro ao parsear data: " + e.getMessage());
            e.printStackTrace();
        }

        repository.save(os);
    }
}