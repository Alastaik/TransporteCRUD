package org.Alastaik.logistica.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "motoristas")
public class Motorista {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String lotacao; // Ex: Vara Cível, Adm, etc.

    @Enumerated(EnumType.STRING)
    private Situacao situacao = Situacao.DISPONIVEL;

    public enum Situacao {
        DISPONIVEL, EM_VIAGEM, INDISPONIVEL
    }
}
