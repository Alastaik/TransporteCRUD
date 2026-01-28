package org.Alastaik.logistica;

import org.Alastaik.logistica.model.OrdemServico;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> baixarRelatorio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        byte[] arquivoExcel = relatorioService.gerarRelatorioExcel(inicio, fim);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio_logistica.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(arquivoExcel);
    }

    // Rota X-RAY para ver se salvou com Data NULL
    @GetMapping("/debug/todos")
    public ResponseEntity<List<OrdemServico>> debugTodos() {
        return ResponseEntity.ok(relatorioService.listarTudoParaDebug());
    }
}