package br.com.fabricads.poc.spawn.actors;

import br.com.fabricads.poc.proto.Keycloak;
import br.com.fabricads.poc.spawn.services.KeycloakService;
import io.eigr.spawn.api.actors.ActorContext;
import io.eigr.spawn.api.actors.Value;
import io.eigr.spawn.api.actors.annotations.Action;
import io.eigr.spawn.api.actors.annotations.TimerAction;
import io.eigr.spawn.api.actors.annotations.stateful.StatefulNamedActor;
import io.eigr.spawn.api.extensions.DependencyInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@StatefulNamedActor(name = "keycloak_actor", stateType = Keycloak.KeycloakState.class, deactivatedTimeout = 3600000L)
public final class KeycloakActor {

    private static final Logger log = LoggerFactory.getLogger(KeycloakActor.class);

    private final KeycloakService keycloakService;

    public KeycloakActor(DependencyInjector injector) {
        this.keycloakService = injector.getInstance(KeycloakService.class);
    }

    @Action
    public Value init(ActorContext<Keycloak.KeycloakState> context) {
        log.debug("init keycloak_actor");
        return keycloakService.adminLogin()
                .map(token -> Value.at()
                        .state(Keycloak.KeycloakState.newBuilder()
                                .setAccessToken(token.getAccessToken())
                                .setRefreshToken(token.getRefreshToken())
                                .build())
                        .response(Keycloak.KeycloakState.newBuilder()
                                .setAccessToken(token.getAccessToken())
                                .setRefreshToken(token.getRefreshToken())
                                .build())
                        .reply()
                )
                .orElseGet(() -> Value.at().empty());
    }

    @TimerAction(period = 60000)
    public Value refreshToken(ActorContext<Keycloak.KeycloakState> context) {
        log.debug("refreshToken keycloak_actor");
        if(context.getState().isPresent()) {
            return keycloakService.refreshAdminToken(context.getState().get().getAccessToken(),
                            context.getState().get().getRefreshToken())
                    .map(token -> Value.at()
                            .state(Keycloak.KeycloakState.newBuilder()
                                    .setAccessToken(token.getAccessToken())
                                    .setRefreshToken(token.getRefreshToken())
                                    .build())
                            .noReply()
                    )
                    .orElseGet(() -> Value.at().empty());
        }
        return keycloakService.adminLogin()
                .map(token -> Value.at()
                        .state(Keycloak.KeycloakState.newBuilder()
                                .setAccessToken(token.getAccessToken())
                                .setRefreshToken(token.getRefreshToken())
                                .build())
                        .noReply()
                )
                .orElseGet(() -> Value.at().empty());
    }
}