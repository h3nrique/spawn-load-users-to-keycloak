package br.com.fabricads.poc.spawn;

import br.com.fabricads.poc.spawn.actors.*;
import br.com.fabricads.poc.spawn.handler.ImportHandler;
import br.com.fabricads.poc.spawn.services.KeycloakService;
import br.com.fabricads.poc.spawn.services.MockService;
import io.eigr.spawn.api.ActorIdentity;
import io.eigr.spawn.api.Spawn;
import io.eigr.spawn.api.TransportOpts;
import io.eigr.spawn.api.extensions.DependencyInjector;
import io.eigr.spawn.api.extensions.SimpleDependencyInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;
import java.util.concurrent.Executors;

public final class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        Config cfg = Config.createDefaultConfig();
        log.debug("Setup application with default config: {}", cfg);

        DependencyInjector dependencyInjector = SimpleDependencyInjector.createInjector();
        dependencyInjector.bind(KeycloakService.class, new KeycloakService(cfg));
        dependencyInjector.bind(MockService.class, new MockService());
        dependencyInjector.bind(Config.class, cfg);

        waitForProxy(cfg.startupDelaySeconds());

        Spawn spawnSystem = new Spawn.SpawnSystem()
                .create(cfg.spawnSystemName())
                .withActor(KeycloakActor.class, dependencyInjector, arg -> new KeycloakActor((DependencyInjector) arg))
                .withActor(ReportActor.class)
                .withActor(UsersGeneratorActor.class, dependencyInjector, arg -> new UsersGeneratorActor((DependencyInjector) arg))
                .withActor(UserActor.class, dependencyInjector, arg -> new UserActor((DependencyInjector) arg))
                .withActor(MockActor.class, dependencyInjector, arg -> new MockActor((DependencyInjector) arg))
                .withActor(MockGeneratorActor.class, dependencyInjector, arg -> new MockGeneratorActor((DependencyInjector) arg))
                .withTerminationGracePeriodSeconds(5)
                .withTransportOptions(TransportOpts.builder()
                        .host(cfg.userFunctionHost())
                        .port(Integer.parseInt(cfg.userFunctionPort()))
                        .proxyHost(cfg.spawnProxyHost())
                        .proxyPort(Integer.parseInt(cfg.spawnProxyPort()))
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .build())
                .build();

        spawnSystem.start();

        RestServer.create(cfg.host(), Integer.parseInt(cfg.port()))
                .withRoute("/import", new ImportHandler(spawnSystem))
                .start();

//        spawnSystem.createActorRef(ActorIdentity.of(cfg.spawnSystemName(), "keycloak_actor")); // Init auth keycloak.

        log.info("Actor running and ready to connection at ports [{}] and [{}]", cfg.userFunctionPort(), cfg.port());
    }

    private static void waitForProxy(String startupDelaySeconds) {
        try {
            log.info("Waiting [{}] seconds to start...", startupDelaySeconds);
            Thread.sleep(Long.parseLong(startupDelaySeconds) * 1000);
        } catch (Exception err) {
        }
    }

    public record Config(String startupDelaySeconds, String host, String port, String userFunctionHost,
                         String userFunctionPort, String spawnProxyHost, String spawnProxyPort,
                         String spawnSystemName, String keycloakAuthUrl, String keycloakClientSecret,
                         String keycloakUsername, String keycloakPassword, String keycloakLoadUsersRealm,
                         Integer backpressureLimit, Integer backpressurePause) {

        public static Config createDefaultConfig() {
            String startupDelaySeconds = System.getenv("STARTUP_DELAY_SECONDS") != null ? System.getenv("STARTUP_DELAY_SECONDS") : "10";
            String host = System.getenv("HOST") != null ? System.getenv("HOST") : "0.0.0.0";
            String port = System.getenv("PORT") != null ? System.getenv("PORT") : "8080";
            String userFunctionHost = System.getenv("USER_FUNCTION_HOST") != null ? System.getenv("USER_FUNCTION_HOST") : "localhost";
            String userFunctionPort = System.getenv("USER_FUNCTION_PORT") != null ? System.getenv("USER_FUNCTION_PORT") : "8091";
            String spawnProxyHost = System.getenv("SPAWN_PROXY_HOST") != null ? System.getenv("SPAWN_PROXY_HOST") : "localhost";
            String spawnProxyPort = System.getenv("SPAWN_PROXY_PORT") != null ? System.getenv("SPAWN_PROXY_PORT") : "9001";
            String spawnSystemName = System.getenv("SPAWN_SYSTEM_NAME") != null ? System.getenv("SPAWN_SYSTEM_NAME") : "spawn-system";
            
            String keycloakAuthUrl = System.getenv("KEYCLOAK_AUTH_URL") != null ? System.getenv("KEYCLOAK_AUTH_URL") : "http://keycloak:8180";
            String keycloakClientSecret = System.getenv("KEYCLOAK_CLIENT_SECRET");
            String keycloakUsername = System.getenv("KEYCLOAK_USERNAME") != null ? System.getenv("KEYCLOAK_USERNAME") : "example";
            String keycloakPassword = System.getenv("KEYCLOAK_PASSWORD") != null ? System.getenv("KEYCLOAK_PASSWORD") : "example4120";
            String keycloakLoadUsersRealm = System.getenv("KEYCLOAK_USERS_REALM") != null ? System.getenv("KEYCLOAK_USERS_REALM") : "master";

            Integer backpressureLimit = System.getenv("BACKPRESSURE_LIMIT") != null ? Integer.parseInt(System.getenv("BACKPRESSURE_LIMIT")) : 5;
            Integer backpressurePause = System.getenv("BACKPRESSURE_PAUSE") != null ? Integer.parseInt(System.getenv("BACKPRESSURE_PAUSE")) : 1000;

            return new Config(startupDelaySeconds, host, port, userFunctionHost, userFunctionPort, spawnProxyHost,
                    spawnProxyPort, spawnSystemName, keycloakAuthUrl, keycloakClientSecret, keycloakUsername,
                    keycloakPassword, keycloakLoadUsersRealm, backpressureLimit, backpressurePause);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Config.class.getSimpleName() + "[", "]")
                    .add("DELAY_SECONDS='" + startupDelaySeconds + "'")
                    .add("HOST='" + host + "'")
                    .add("PORT='" + port + "'")
                    .add("USER_FUNCTION_HOST='" + userFunctionHost + "'")
                    .add("USER_FUNCTION_PORT='" + userFunctionPort + "'")
                    .add("SPAWN_PROXY_HOST='" + spawnProxyHost + "'")
                    .add("SPAWN_PROXY_PORT='" + spawnProxyPort + "'")
                    .add("SPAWN_SYSTEM_NAME='" + spawnSystemName + "'")
                    .add("KEYCLOAK_AUTH_URL='" + keycloakAuthUrl + "'")
                    .add("KEYCLOAK_CLIENT_SECRET='" + keycloakClientSecret + "'")
                    .add("KEYCLOAK_USERNAME='" + keycloakUsername + "'")
                    .add("KEYCLOAK_PASSWORD='" + keycloakPassword + "'")
                    .toString();
        }
    }
}