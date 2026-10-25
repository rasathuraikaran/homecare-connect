package com.homecare.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Checks signature, audience and expiry against Google's public keys. */
@Component
public class GoogleIdTokenService implements GoogleTokenVerifier {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenService(@Value("${app.google.client-id}") String clientId) {
        this.clientId = clientId;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }

    @Override
    public Identity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured");
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google sign-in");
            }
            var p = token.getPayload();
            return new Identity(p.getSubject(), p.getEmail(), (String) p.get("name"));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google sign-in");
        }
    }
}
