package br.com.fabricads.poc.spawn;

import br.com.fabricads.poc.spawn.actors.*;
import br.com.fabricads.poc.spawn.services.KeycloakService;
import br.com.fabricads.poc.spawn.services.MockService;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import io.eigr.spawn.api.Spawn;
import io.eigr.spawn.api.TransportOpts;
import io.eigr.spawn.api.exceptions.SpawnException;
import io.eigr.spawn.api.extensions.DependencyInjector;
import io.eigr.spawn.api.extensions.SimpleDependencyInjector;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.concurrent.Executors;

abstract class AbstractContainerBaseTest {

    private static final Network NETWORK = Network.newNetwork();
    static final GenericContainer<?> KEYCLOAK_CONTAINER;
    static final GenericContainer<?> MARIADB_CONTAINER;
    static final GenericContainer<?> NATS_CONTAINER;
    static final GenericContainer<?> SPAWN_CONTAINER;
    static final Spawn spawnSystem;
    static final App.Config cfg;

    static {
        Testcontainers.exposeHostPorts(8091);
        KEYCLOAK_CONTAINER = new GenericContainer<>("quay.io/keycloak/keycloak:23.0")
                .withNetwork(NETWORK)
                .withNetworkAliases("keycloak-test")
                .withExposedPorts(8080)
                .withCommand("start-dev")
                .withEnv("TZ", "America/Sao_Paulo")
                .withEnv("KEYCLOAK_ADMIN", "example")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "example4120")
                .waitingFor(Wait.forListeningPorts(8080));
        KEYCLOAK_CONTAINER.start();

        MARIADB_CONTAINER = new GenericContainer<>("mariadb:latest")
                .withNetwork(NETWORK)
                .withNetworkAliases("mariadb-test")
                .withEnv("TZ", "America/Sao_Paulo")
                .withEnv("MYSQL_DATABASE", "eigr")
                .withEnv("MYSQL_ROOT_PASSWORD", "admin")
                .withEnv("MYSQL_USER", "admin")
                .withEnv("MYSQL_PASSWORD", "admin")
                .withExposedPorts(3306)
                .waitingFor(Wait.forListeningPorts(3306));
        MARIADB_CONTAINER.start();

        NATS_CONTAINER = new GenericContainer<>("nats:2")
                .withNetwork(NETWORK)
                .withNetworkAliases("nats-test");
        NATS_CONTAINER.start();

        SPAWN_CONTAINER = new GenericContainer<>("eigr/spawn-proxy:1.1.1")
//                .withCreateContainerCmdModifier(e -> e.withHostConfig(HostConfig.newHostConfig()
//                        .withPortBindings(PortBinding.parse("9001:9001"))))
                .withNetwork(NETWORK)
                .withNetworkAliases("spawn-test")
                .withNetworkMode("host")
                .withPrivilegedMode(true)
                .dependsOn(MARIADB_CONTAINER)
                .withExposedPorts(9001)
                .withAccessToHost(true)
                .withEnv("SPAWN_USE_INTERNAL_NATS", "false")
                .withEnv("SPAWN_PUBSUB_ADAPTER", "nats")
                .withEnv("SPAWN_PUBSUB_NATS_HOSTS", "nats://nats-test:4222")
                .withEnv("SPAWN_STATESTORE_KEY", "3Jnb0hZiHIzHTOih7t2cTEPEpY98Tu1wvQkPfq/XwqE=")
                .withEnv("SPAWN_SUPERVISORS_STATE_HANDOFF_CONTROLLER", "crdt")
                .withEnv("PROXY_HTTP_CLIENT_ADAPTER_POOL_SCHEDULERS", "9")
                .withEnv("PROXY_HTTP_CLIENT_ADAPTER_POOL_SIZE", "100")
                .withEnv("PROXY_HTTP_CLIENT_ADAPTER_POOL_MAX_IDLE_TIMEOUT", "1000")
                .withEnv("PROXY_APP_NAME", "spawn")
                .withEnv("PROXY_CLUSTER_STRATEGY", "gossip")
                .withEnv("PROXY_DATABASE_TYPE", "mariadb")
                .withEnv("PROXY_DATABASE_HOST", "mariadb-test")
                .withEnv("PROXY_DATABASE_PORT", "3306")
                .withEnv("PROXY_DATABASE_NAME", "eigr")
                .withEnv("PROXY_HTTP_PORT", "9001")
                .withEnv("USER_FUNCTION_PORT", "8091")
                .withEnv("USER_FUNCTION_HOST", "host.testcontainers.internal")
                .withEnv("SPAWN_USE_INTERNAL_NATS", "false");
        SPAWN_CONTAINER.start();

        String keycloakAddr = "http://".concat(KEYCLOAK_CONTAINER.getHost()).concat(":").concat(KEYCLOAK_CONTAINER.getFirstMappedPort().toString());
//        String keycloakAddr = "http://localhost:8180";
        cfg = new App.Config("5", "0.0.0.0", "8080", "localhost", "8091", SPAWN_CONTAINER.getHost(),
                "9001", "spawn-system-test", keycloakAddr, null, "example",
                "example4120", "master", 5, 1000);
        DependencyInjector dependencyInjector = SimpleDependencyInjector.createInjector();
        dependencyInjector.bind(KeycloakService.class, new KeycloakService(cfg));
        dependencyInjector.bind(MockService.class, new MockService());
        dependencyInjector.bind(App.Config.class, cfg);

        spawnSystem = new Spawn.SpawnSystem()
                .create(cfg.spawnSystemName())
                .withActor(KeycloakActor.class, dependencyInjector, arg -> new KeycloakActor((DependencyInjector) arg))
                .withActor(ReportActor.class)
                .withActor(UsersGeneratorActor.class, dependencyInjector, arg -> new UsersGeneratorActor((DependencyInjector) arg))
                .withActor(UserActor.class, dependencyInjector, arg -> new UserActor((DependencyInjector) arg))
                .withActor(MockActor.class, dependencyInjector, arg -> new MockActor((DependencyInjector) arg))
                .withActor(MockGeneratorActor.class, dependencyInjector, arg -> new MockGeneratorActor((DependencyInjector) arg))
                .withTerminationGracePeriodSeconds(10)
                .withTransportOptions(TransportOpts.builder()
                        .host(cfg.userFunctionHost())
                        .port(Integer.parseInt(cfg.userFunctionPort()))
                        .proxyHost(cfg.spawnProxyHost())
                        .proxyPort(Integer.parseInt(cfg.spawnProxyPort()))
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .build())
                .build();

        try {
            spawnSystem.start();
        } catch (SpawnException e) {
            throw new RuntimeException(e);
        }
    }
}
