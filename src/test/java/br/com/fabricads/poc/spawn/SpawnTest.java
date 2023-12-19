package br.com.fabricads.poc.spawn;

import br.com.fabricads.poc.proto.Keycloak;
import br.com.fabricads.poc.proto.Mock;
import io.eigr.spawn.api.ActorIdentity;
import io.eigr.spawn.api.exceptions.SpawnException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpawnTest extends AbstractContainerBaseTest {

    private static final Logger log = LoggerFactory.getLogger(SpawnTest.class);

    @Test
    public void simpleTest() throws SpawnException {
        // TODO :: Proxy container can't callback host function, by network limitation between container and host.
//        spawnSystem.createActorRef(ActorIdentity.of(cfg.spawnSystemName(), "keycloak_actor"))
//                .invoke("init", Keycloak.KeycloakState.class)
//                .ifPresent(ks -> {
//                    log.info("keycloak_actor init :: accessToken = {}", ks.getAccessToken());
//                    log.info("keycloak_actor init :: refreshToken = {}", ks.getRefreshToken());
//                });
    }

    @Test
    public void mockTest() throws SpawnException {
//        spawnSystem.createActorRef(ActorIdentity.of(cfg.spawnSystemName(), "mock_generator"))
//                .invokeAsync("generate", Mock.MockGeneratorRequest.newBuilder()
//                        .setQdte(100)
//                        .build());
    }
}
