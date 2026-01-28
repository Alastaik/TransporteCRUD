package org.Alastaik.logistica;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LogisticsData(
                @JsonProperty("nome_solicitante") String nomeSolicitante,
                @JsonProperty("destino") String destino,
                @JsonProperty("data_hora_iso") String dataHoraIso,
                @JsonProperty("passageiros") List<String> passageiros,
                @JsonProperty("aguardar_retorno") Boolean aguardarRetorno,
                @JsonProperty("proad") String proad,
                @JsonProperty("tipo_veiculo") String tipoVeiculo) {
}
