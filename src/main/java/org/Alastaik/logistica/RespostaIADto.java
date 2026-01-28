package org.Alastaik.logistica;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RespostaIADto(
        @JsonProperty("mensagemParaUsuario") String mensagemParaUsuario,
        @JsonProperty("dados") DadosExtraidos dados) {
}
