package org.Alastaik.logistica.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "veiculos")
public class Veiculo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String modelo; // Ex: Corolla, Ranger
    private String placa;
    private String cor;

    @Enumerated(EnumType.STRING)
    private Situacao situacao = Situacao.DISPONIVEL;

    public enum Situacao {
        DISPONIVEL, EM_VIAGEM, MANUTENCAO
    }
}
