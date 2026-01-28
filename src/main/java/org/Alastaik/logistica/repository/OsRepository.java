package org.Alastaik.logistica.repository;

import org.Alastaik.logistica.model.OrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OsRepository extends JpaRepository<OrdemServico, Long> {

    // Método para o relatório Excel: buscar por período
    List<OrdemServico> findByDataHoraSaidaBetween(LocalDateTime inicio, LocalDateTime fim);
}