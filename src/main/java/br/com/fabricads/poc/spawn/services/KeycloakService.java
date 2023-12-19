package br.com.fabricads.poc.spawn.services;

import br.com.fabricads.poc.spawn.pojo.AuthKeycloakRepresentation;
import br.com.fabricads.poc.spawn.pojo.UserKeycloakRepresentation;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.fabricads.poc.spawn.App.Config;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class KeycloakService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakService.class);
    public static final String JSON_MEDIA = "application/json";
    public static final MediaType JSON_MEDIA_TYPE = MediaType.get(JSON_MEDIA);
    public static final String URL_ENCODED_MEDIA = "application/x-www-form-urlencoded";
    public static final MediaType URL_ENCODED_MEDIA_TYPE = MediaType.get(URL_ENCODED_MEDIA);

    final OkHttpClient client;
    final String authUrl;
    final String username;
    final String password;
    final String clientSecret;
    final String realm;

    public KeycloakService(Config cfg) {
        this.authUrl = cfg.keycloakAuthUrl();
        this.username = cfg.keycloakUsername();
        this.password = cfg.keycloakPassword();
        this.clientSecret = cfg.keycloakClientSecret();
        this.realm = cfg.keycloakLoadUsersRealm();
        client = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    public Optional<AuthKeycloakRepresentation> adminLogin() {
        String body = String.format("grant_type=password&client_id=admin-cli&username=%s&password=%s", username, password);
        if(clientSecret != null && !clientSecret.isEmpty()) {
            body = body.concat("&client_secret=").concat(clientSecret);
        }
        RequestBody requestBody = RequestBody.create(body, URL_ENCODED_MEDIA_TYPE);
        Request request = new Request.Builder()
            .url(authUrl.concat("/realms/master/protocol/openid-connect/token"))
            .post(requestBody)
            .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response adminLogin [{}]", responseBody);
                Type type = new TypeToken<AuthKeycloakRepresentation>() { }.getType();
                return Optional.of(new Gson().fromJson(responseBody, type));
            } else {
                log.warn("Error response adminLogin :: [{}]", response.code());
            }
        } catch (Exception err) {
            log.error("Error while adminLogin :: [{}]", err.getMessage());
            log.trace("Error while adminLogin", err);
        }
        return Optional.empty();
    }

    public Optional<AuthKeycloakRepresentation> refreshAdminToken(String accessToken, String refreshToken) {
        String body = String.format("grant_type=refresh_token&client_id=admin-cli&refresh_token=%s", refreshToken);
        if(clientSecret != null && !clientSecret.isEmpty()) {
            body = body.concat("&client_secret=").concat(clientSecret);
        }
        RequestBody requestBody = RequestBody.create(body, URL_ENCODED_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(authUrl.concat("/realms/master/protocol/openid-connect/token"))
                .addHeader("Authorization", "Bearer ".concat(accessToken))
                .post(requestBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response refreshAdminToken [{}]", responseBody);
                Type type = new TypeToken<AuthKeycloakRepresentation>() { }.getType();
                return Optional.of(new Gson().fromJson(responseBody, type));
            } else {
                log.warn("Error response refreshAdminToken :: [{}]", response.code());
                return adminLogin();
            }
        } catch (Exception err) {
            log.error("Error while refreshAdminToken :: [{}]", err.getMessage());
            log.trace("Error while refreshAdminToken", err);
        }
        return Optional.empty();
    }

    public Optional<UserKeycloakRepresentation[]> findUser(String accessToken, String username) {
        String query = "&exact=true&first=0&max=1";
        Request request = new Request.Builder()
                .url(authUrl.concat(String.format("/admin/realms/%s/users?username=%s", realm, username)).concat(query))
                .addHeader("Authorization", "Bearer ".concat(accessToken))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response findUser [{}]", responseBody);
                Type type = new TypeToken<UserKeycloakRepresentation[]>() { }.getType();
                UserKeycloakRepresentation[] userRepresentations = new Gson().fromJson(responseBody, type);
                if(userRepresentations.length > 0) {
                    return Optional.of(userRepresentations);
                }
            } else {
                log.warn("Error response findUser [{}] :: [{}]", username, response.code());
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
        return Optional.empty();
    }

    public Optional<Boolean> updateUser(String accessToken, String userId, Map<String, String[]> attributes) {
        String json = new Gson().toJson(attributes);
        String jsonBody = String.format("{\"attributes\":%s}", json);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(authUrl.concat(String.format("/admin/realms/%s/users/%s", realm, userId)))
                .addHeader("Authorization", "Bearer ".concat(accessToken))
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response updateUser [{}]", responseBody);
                return Optional.of(Boolean.TRUE);
            } else {
                log.warn("Error response updateUser [{}] :: [{}]", username, response.code());
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
        return Optional.empty();
    }

    public Optional<Boolean> createUser(String accessToken, UserKeycloakRepresentation userRepresentation) {
        String jsonBody = new Gson().toJson(userRepresentation);
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(authUrl.concat(String.format("/admin/realms/%s/users", realm)))
                .addHeader("Authorization", "Bearer ".concat(accessToken))
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response createUser [{}]", responseBody);
                return Optional.of(Boolean.TRUE);
            } else {
                log.warn("Error response createUser [{}] :: [{}]", userRepresentation.getUsername(), response.code());
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
        return Optional.empty();
    }

    public Optional<String> logout(String accessToken, String refreshToken) {
        String body = String.format("client_id=admin-cli&refresh_token=%s", refreshToken);
        RequestBody requestBody = RequestBody.create(body, URL_ENCODED_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(authUrl.concat("/realms/master/protocol/openid-connect/logout"))
                .addHeader("Authorization", "Bearer ".concat(accessToken))
                .post(requestBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response logout [{}]", responseBody);
                Type type = new TypeToken<String>() { }.getType();
                return Optional.of(new Gson().fromJson(responseBody.isEmpty() ? "{}" : responseBody, type));
            } else {
                log.warn("Error response logout :: [{}]", response.code());
            }
        } catch (Exception err) {
            log.error("Error while logout :: [{}]", err.getMessage());
            log.trace("Error while logout", err);
        }
        return Optional.empty();
    }

    public Optional<String> count(String accessToken) {
        Request request = new Request.Builder()
                .url(authUrl.concat(String.format("/admin/realms/%s/users/count", realm)))
                .addHeader("Authorization", "Bearer ".concat(accessToken))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()) {
                assert response.body() != null;
                String responseBody = response.body().string();
                log.trace("Response count keycloak [{}]", responseBody);
                Type type = new TypeToken<String>() { }.getType();
                return Optional.of(new Gson().fromJson(responseBody, type));
            } else {
                log.warn("Error response count keycloak :: [{}]", response.code());
            }
        } catch (Exception err) {
            log.error("Error while count", err);
        }
        return Optional.empty();
    }
}
