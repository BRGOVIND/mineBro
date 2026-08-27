package com.minebro.provider.http;

/** Keeps API keys out of logs and exceptions - the only place a key's value is allowed to flow to text. */
public final class SecretRedactor {

    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(none)";
        }
        int keep = Math.min(4, secret.length());
        return "*".repeat(Math.max(0, secret.length() - keep)) + secret.substring(secret.length() - keep);
    }

    private SecretRedactor() {}
}
