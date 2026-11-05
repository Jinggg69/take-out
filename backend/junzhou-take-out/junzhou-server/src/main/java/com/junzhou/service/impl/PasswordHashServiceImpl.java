package com.junzhou.service.impl;

import com.junzhou.service.PasswordHashService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PasswordHashServiceImpl implements PasswordHashService {
    private static final String BCRYPT = "{bcrypt}";
    private static final String BCRYPT_SHA256 = "{bcrypt-sha256}";
    private static final Pattern LEGACY = Pattern.compile("[a-fA-F0-9]{32}");
    private static final Pattern BCRYPT_VALUE = Pattern.compile("\\$2[aby]\\$(0[4-9]|1[0-6])\\$[./A-Za-z0-9]{53}");
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Override
    public String encode(String password) {
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("密码不能为空");
        if (requiresPrehash(password)) return BCRYPT_SHA256 + encoder.encode(prehash(password));
        return BCRYPT + encoder.encode(password);
    }

    @Override
    public boolean matches(String password, String storedHash) {
        if (password == null || password.isEmpty() || storedHash == null) return false;
        if (needsUpgrade(storedHash)) {
            String actual = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    storedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        }
        String candidate;
        String encoded;
        if (storedHash.startsWith(BCRYPT) && !requiresPrehash(password)) {
            candidate = password;
            encoded = storedHash.substring(BCRYPT.length());
        } else if (storedHash.startsWith(BCRYPT_SHA256)) {
            candidate = prehash(password);
            encoded = storedHash.substring(BCRYPT_SHA256.length());
        } else {
            return false;
        }
        return BCRYPT_VALUE.matcher(encoded).matches() && encoder.matches(candidate, encoded);
    }

    @Override
    public boolean needsUpgrade(String storedHash) {
        return storedHash != null && LEGACY.matcher(storedHash).matches();
    }

    // Preserve every byte of legacy passwords outside BCrypt's normal input range.
    private boolean requiresPrehash(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72 || password.indexOf(0) >= 0;
    }

    private String prehash(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
