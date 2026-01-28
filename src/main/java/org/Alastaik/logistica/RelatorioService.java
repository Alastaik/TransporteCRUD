package org.Alastaik.logistica;

import org.Alastaik.logistica.model.OrdemServico;
import org.Alastaik.logistica.repository.OsRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RelatorioService {

    private final OsRepository repository;

    public RelatorioService(OsRepository repository) {
        this.repository = repository;
    }

    // Método Debug "X-Ray"
    public List<OrdemServico> listarTudoParaDebug() {
        return repository.findAll();
    }

    public byte[] gerarRelatorioExcel(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(23, 59, 59);

        List<OrdemServico> lista = repository.findByDataHoraSaidaBetween(dataInicio, dataFim);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Solicitações");

            // Estilos
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy HH:mm"));

            // Cabeçalho (Adicionado campo PROAD, Motorista, Veículo)
            Row header = sheet.createRow(0);
            String[] colunas = { "ID", "Solicitante", "Passageiros", "Destino", "Saída", "Aguardar?", "WhatsApp",
                    "PROAD", "Motorista", "Veículo", "Observações" };
            for (int i = 0; i < colunas.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(colunas[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dados
            int rowNum = 1;
            for (OrdemServico os : lista) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(os.getId());
                row.createCell(1).setCellValue(os.getNomeSolicitante());
                row.createCell(2).setCellValue(os.getPassageiros());
                row.createCell(3).setCellValue(os.getDestino());

                Cell dateCell = row.createCell(4);
                if (os.getDataHoraSaida() != null) {
                    dateCell.setCellValue(os.getDataHoraSaida());
                    dateCell.setCellStyle(dateStyle);
                } else {
                    dateCell.setCellValue("DATA NULA (ERRO)");
                }

                row.createCell(5).setCellValue(os.isAguardarRetorno() ? "SIM" : "NÃO");
                row.createCell(6).setCellValue(os.getNumeroWhatsapp());
                row.createCell(7).setCellValue(os.getProad() != null ? os.getProad() : "-");

                // Motorista
                String motoristaNome = (os.getMotorista() != null) ? os.getMotorista().getNome() : "Não atribuído";
                row.createCell(8).setCellValue(motoristaNome);

                // Veículo
                String veiculoDesc = (os.getVeiculo() != null)
                        ? os.getVeiculo().getModelo() + " (" + os.getVeiculo().getPlaca() + ")"
                        : "Não atribuído";
                row.createCell(9).setCellValue(veiculoDesc);

                row.createCell(10).setCellValue(os.getObservacoes());
            }

            // Auto-size colunas
            for (int i = 0; i < colunas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel", e);
        }
    }
}