package org.Alastaik.logistica.model;

import jakarta.persistence.*;
import lombok.Data; // Lombok gera Getters/Setters/ToString automaticamente
import java.time.LocalDateTime;

@Entity
@Data // Essa anotação faz a mágica de criar getters e setters
@Table(name = "ordens_servico")
public class OrdemServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Nossa "OS Autoincrement"

    private String nomeSolicitante; // Quem pediu (extraído do WhatsApp ou texto)

    private String destino; // Para onde vão

    private LocalDateTime dataHoraSaida; // Dia e Horário

    private String passageiros; // Nomes ou quantidade

    private boolean aguardarRetorno; // Sim ou Não

    private String numeroWhatsapp; // ID de quem pediu (pra gente responder depois)

    private String proad; // Número do PROAD para viagens fora de Goiânia

    private String tipoVeiculo; // Tipo de veículo: Carro Convencional, Caminhonete, Van

    @Column(columnDefinition = "TEXT")
    private String observacoes; // Caso a IA pegue algo extra

    // RELACIONAMENTOS PARA FROTA E EQUIPE
    @ManyToOne
    @JoinColumn(name = "motorista_id")
    private Motorista motorista;

    @ManyToOne
    @JoinColumn(name = "veiculo_id")
    private Veiculo veiculo;

    // Data de criação do registro no sistema
    private LocalDateTime criadoEm = LocalDateTime.now();
}