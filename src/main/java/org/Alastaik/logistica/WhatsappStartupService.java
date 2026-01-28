package org.Alastaik.logistica;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class WhatsappStartupService {

    private final WhatsappController whatsappController;

    public WhatsappStartupService(WhatsappController whatsappController) {
        this.whatsappController = whatsappController;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Aguarda 5 segundos para garantir que o container do WhatsApp (e Spring Boot)
        // esteja totalmente pronto
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(this::tryStartSession, 5, TimeUnit.SECONDS);
    }

    private void tryStartSession() {
        System.out.println("🤖 WhatsappStartupService: Tentando iniciar sessão do WhatsApp automaticamente...");
        try {
            whatsappController.startSession();
            System.out.println("✅ WhatsappStartupService: Solicitação de início de sessão enviada com sucesso.");
        } catch (Exception e) {
            System.err.println(
                    "❌ WhatsappStartupService: Falha ao tentar iniciar sessão automaticamente: " + e.getMessage());
        }
    }
}
