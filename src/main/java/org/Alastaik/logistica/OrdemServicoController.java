package org.Alastaik.logistica;

import org.Alastaik.logistica.model.OrdemServico;
import org.Alastaik.logistica.repository.OsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/os")
public class OrdemServicoController {

    private final OsRepository repository;

    public OrdemServicoController(OsRepository repository) {
        this.repository = repository;
    }

    // LISTAR TODAS (Mais recentes primeiro)
    @GetMapping
    public List<OrdemServico> listarTodas() {
        // Idealmente ordenaria por dataHoraSaida DESC, mas faremos simples por hora
        return repository.findAll();
    }

    // BUSCAR POR ID
    @GetMapping("/{id}")
    public ResponseEntity<OrdemServico> buscarPorId(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // CRIAR NOVA ORDEM (MANUALMENTE)
    @PostMapping
    public ResponseEntity<OrdemServico> criar(@RequestBody OrdemServico novaOs) {
        novaOs.setId(null); // Garante que é criação
        novaOs.setCriadoEm(LocalDateTime.now());
        if (novaOs.getTipoVeiculo() == null)
            novaOs.setTipoVeiculo("Carro Convencional");
        return ResponseEntity.ok(repository.save(novaOs));
    }

    // ATUALIZAR
    @PutMapping("/{id}")
    public ResponseEntity<OrdemServico> atualizar(@PathVariable Long id, @RequestBody OrdemServico novosDados) {
        return repository.findById(id)
                .map(os -> {
                    os.setNomeSolicitante(novosDados.getNomeSolicitante());
                    os.setDestino(novosDados.getDestino());
                    os.setDataHoraSaida(novosDados.getDataHoraSaida());
                    os.setPassageiros(novosDados.getPassageiros());
                    os.setAguardarRetorno(novosDados.isAguardarRetorno());
                    os.setProad(novosDados.getProad());
                    os.setObservacoes(novosDados.getObservacoes());
                    // Não atualizamos ID nem criadoEm
                    return ResponseEntity.ok(repository.save(os));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETAR
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // LIMPAR TUDO (PERIGO)
    @DeleteMapping("/wipe-all-secret-key")
    public ResponseEntity<Void> deletarTudo() {
        repository.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
