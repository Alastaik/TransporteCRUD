package org.Alastaik.logistica;

import org.Alastaik.logistica.model.Motorista;
import org.Alastaik.logistica.model.Veiculo;
import org.Alastaik.logistica.repository.MotoristaRepository;
import org.Alastaik.logistica.repository.VeiculoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recursos")
public class RecursosController {

    private final MotoristaRepository motoristaRepository;
    private final VeiculoRepository veiculoRepository;

    public RecursosController(MotoristaRepository motoristaRepository, VeiculoRepository veiculoRepository) {
        this.motoristaRepository = motoristaRepository;
        this.veiculoRepository = veiculoRepository;
    }

    // --- MOTORISTAS ---
    @GetMapping("/motoristas")
    public List<Motorista> listarMotoristas() {
        return motoristaRepository.findAll();
    }

    @PostMapping("/motoristas")
    public Motorista salvarMotorista(@RequestBody Motorista motorista) {
        return motoristaRepository.save(motorista);
    }

    @PutMapping("/motoristas/{id}")
    public ResponseEntity<Motorista> atualizarMotorista(@PathVariable Long id, @RequestBody Motorista dados) {
        return motoristaRepository.findById(id).map(m -> {
            m.setNome(dados.getNome());
            m.setLotacao(dados.getLotacao());
            m.setSituacao(dados.getSituacao());
            return ResponseEntity.ok(motoristaRepository.save(m));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/motoristas/{id}")
    public ResponseEntity<?> deletarMotorista(@PathVariable Long id) {
        try {
            motoristaRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(409)
                    .body("Não é possível excluir este motorista pois ele está vinculado a ordens de serviço.");
        }
    }

    // --- VEICULOS ---
    @GetMapping("/veiculos")
    public List<Veiculo> listarVeiculos() {
        return veiculoRepository.findAll();
    }

    @PostMapping("/veiculos")
    public Veiculo salvarVeiculo(@RequestBody Veiculo veiculo) {
        return veiculoRepository.save(veiculo);
    }

    @PutMapping("/veiculos/{id}")
    public ResponseEntity<Veiculo> atualizarVeiculo(@PathVariable Long id, @RequestBody Veiculo dados) {
        return veiculoRepository.findById(id).map(v -> {
            v.setModelo(dados.getModelo());
            v.setPlaca(dados.getPlaca());
            v.setCor(dados.getCor());
            v.setSituacao(dados.getSituacao());
            return ResponseEntity.ok(veiculoRepository.save(v));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/veiculos/{id}")
    public ResponseEntity<?> deletarVeiculo(@PathVariable Long id) {
        try {
            veiculoRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(409)
                    .body("Não é possível excluir este veículo pois ele está vinculado a ordens de serviço.");
        }
    }
}
