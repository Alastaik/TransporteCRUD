package org.Alastaik.logistica.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class UserDataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UserDataSeeder(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        createIfNotExists("Lucas");
        createIfNotExists("Yuri");
    }

    private void createIfNotExists(String username) {
        if (usuarioRepository.findByUsername(username).isEmpty()) {
            String rawPassword = generateRandomPassword();
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            usuarioRepository.save(user);

            System.out.println("==================================================");
            System.out.println("✅ USUÁRIO CRIADO: " + username);
            System.out.println("🔑 SENHA: " + rawPassword);
            System.out.println("==================================================");
        }
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[8]; // 64 bits = safe enough for this context
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
