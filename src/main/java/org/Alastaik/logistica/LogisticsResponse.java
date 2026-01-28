package org.Alastaik.logistica;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LogisticsResponse(
        String raciocinio,
        Status status,
        LogisticsData dados,
        @JsonAlias("message") @JsonProperty("mensagem_usuario") String mensagemUsuario) {
    public enum Status {
        INCOMPLETE, COMPLETED, IGNORE
    }
}
