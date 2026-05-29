package br.com.casamento.auth.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordService {

    private static final int COST = 12;

    public String hash(String rawPassword) {
        return BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray());
    }

    public boolean matches(String rawPassword, String storedHash) {
        BCrypt.Result result = BCrypt.verifyer().verify(rawPassword.toCharArray(), storedHash);
        return result.verified;
    }
}
