package com.cameraman.storage.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedLinkServiceTest {

    private final SignedLinkService service = new SignedLinkService("a-long-test-secret-value-1234567890", "http://api.test/api/");

    @Test
    void validTokenReturnsTheKey() {
        String token = service.createToken("gdrive:1AbC", Duration.ofMinutes(10), 1_000);
        assertThat(service.verify(token, 1_100)).contains("gdrive:1AbC");
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service.createToken("gdrive:1AbC", Duration.ofMinutes(10), 1_000);
        assertThat(service.verify(token, 1_000 + 601)).isEmpty();
    }

    @Test
    void tamperedKeyOrExpiryIsRejected() {
        String token = service.createToken("gdrive:1AbC", Duration.ofMinutes(10), 1_000);
        String[] p = token.split("\\.");
        String otherKey = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("gdrive:OTHER".getBytes());
        assertThat(service.verify(otherKey + "." + p[1] + "." + p[2], 1_100)).isEmpty();
        assertThat(service.verify(p[0] + "." + (Long.parseLong(p[1]) + 99_999) + "." + p[2], 1_100)).isEmpty();
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        SignedLinkService other = new SignedLinkService("a-completely-different-secret-9876543210", "http://api.test/api");
        String token = other.createToken("gdrive:1AbC", Duration.ofMinutes(10), 1_000);
        assertThat(service.verify(token, 1_100)).isEmpty();
    }

    @Test
    void garbageIsRejectedNotThrown() {
        assertThat(service.verify(null, 1)).isEmpty();
        assertThat(service.verify("", 1)).isEmpty();
        assertThat(service.verify("a.b", 1)).isEmpty();
        assertThat(service.verify("a.b.c.d", 1)).isEmpty();
        assertThat(service.verify("!!!.123.sig", 1)).isEmpty();
    }

    @Test
    void urlUsesTheConfiguredApiBaseWithoutDoubleSlash() {
        assertThat(service.createUrl("gdrive:1", Duration.ofMinutes(1))).startsWith("http://api.test/api/files/");
    }

    @Test
    void blankSecretIsRefused() {
        assertThatThrownBy(() -> new SignedLinkService(" ", "http://x")).isInstanceOf(IllegalStateException.class);
    }
}
