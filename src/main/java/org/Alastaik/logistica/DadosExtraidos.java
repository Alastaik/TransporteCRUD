package org.Alastaik.logistica;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DadosExtraidos(
        @JsonProperty("nome") String nome,
        @JsonProperty("destino") String destino,
        @JsonProperty("dataHora") String dataHora,
        @JsonProperty("passageiros") List<String> passageiros,
        @JsonProperty("aguardar") boolean aguardar,
        @JsonProperty("proad") String proad,
        @JsonProperty("finalizado") boolean finalizado) {
}