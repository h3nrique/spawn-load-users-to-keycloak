package br.com.fabricads.poc.spawn.actors;

import br.com.fabricads.poc.proto.Common;
import br.com.fabricads.poc.proto.Keycloak;
import br.com.fabricads.poc.proto.Report;
import br.com.fabricads.poc.proto.User;
import br.com.fabricads.poc.spawn.pojo.UserKeycloakRepresentation;
import br.com.fabricads.poc.spawn.services.KeycloakService;
import io.eigr.spawn.api.ActorIdentity;
import io.eigr.spawn.api.ActorRef;
import io.eigr.spawn.api.actors.ActorContext;
import io.eigr.spawn.api.actors.Value;
import io.eigr.spawn.api.actors.annotations.Action;
import io.eigr.spawn.api.actors.annotations.TimerAction;
import io.eigr.spawn.api.actors.annotations.stateful.StatefulUnNamedActor;
import io.eigr.spawn.api.actors.workflows.SideEffect;
import io.eigr.spawn.api.exceptions.ActorCreationException;
import io.eigr.spawn.api.extensions.DependencyInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@StatefulUnNamedActor(name = "user_actor", stateType = User.UserState.class, deactivatedTimeout = 3600000L)
public final class UserActor {

    private static final Logger log = LoggerFactory.getLogger(UserActor.class);

    private final KeycloakService keycloakService;

    public UserActor(DependencyInjector injector) {
        this.keycloakService = injector.getInstance(KeycloakService.class);
    }

    @Action
    public Value onCreate(User.UserRequest msg, ActorContext<User.UserState> context) throws ActorCreationException {
        ActorRef actorRef = makeRef(context, msg.getState().getUsername());
        return Value.at()
                .state(msg.getState())
                .flow(SideEffect.to(actorRef, "createOrUpdateIdentity", msg))
                .noReply();
    }

    @Action
    public Value createOrUpdateIdentity(User.UserRequest msg, ActorContext<User.UserState> context) {
        String actionType = null;
        String username = msg.getState().getUsername();
        User.UserState.Builder builder = User.UserState.newBuilder(msg.getState());
        try {
            log.debug("Received invocation. Message: '{}'. Context: '{}'.", msg, context);

            if (checkIfProcessAlreadyDone(context)) {
                return Value.at().noReply();
            }

            Optional<Keycloak.KeycloakState> keycloakState = context.getSpawnSystem()
                    .createActorRef(ActorIdentity.of(context.getSpawnSystem().getSystem(), "keycloak_actor"))
                    .invoke("get", Keycloak.KeycloakState.class);
            if(keycloakState.isEmpty()) {
                throw new RuntimeException("keycloak login error");
            }
            String accessToken = keycloakState.get().getAccessToken();

            log.debug("Checking if user [{}] exists", msg.getState().getUsername());
            Optional<UserKeycloakRepresentation[]> userOptional = keycloakService.findUser(accessToken,
                    msg.getState().getUsername());

            if (userOptional.isPresent()) {
                actionType = "update";
                log.debug("User [{}] found", msg.getState().getUsername());
                UserKeycloakRepresentation userRepresentation = userOptional.get()[0];
                AtomicBoolean update = new AtomicBoolean(false);

                setUserAttributes(msg, userRepresentation, update);
                if (update.get()) {
                    keycloakService.updateUser(accessToken, userRepresentation.getId(), userRepresentation.getAttributes());
                    log.debug("User [{}] updated", msg.getState().getUsername());
                    return buildValueWith(context, Common.Status.UPDATED, username, builder);
                }

                return buildValueWith(context, Common.Status.FOUND, username, builder);
            }

            actionType = "create";
            log.debug("User [{}] not found", msg.getState().getUsername());
            UserKeycloakRepresentation userRepresentation = new UserKeycloakRepresentation();
            userRepresentation.setEnabled(true);
            userRepresentation.setEmailVerified(true);
            userRepresentation.setUsername(msg.getState().getUsername());
            userRepresentation.setEmail(msg.getState().getEmail());
            userRepresentation.setFirstName(msg.getState().getFirstName());
            userRepresentation.setLastName(msg.getState().getLastName());

            setUserAttributes(msg, userRepresentation, new AtomicBoolean(false));

            Optional<Boolean> response = keycloakService.createUser(accessToken, userRepresentation);
            if (response.isEmpty()) {
                log.debug("User [{}] or email [{}] already exists", msg.getState().getUsername(), msg.getState().getEmail()); //LGPD compliance
                return buildValueWith(context, Common.Status.CONFLICT, username, builder);
            }
            log.debug("User [{}] created", msg.getState().getUsername());
            return buildValueWith(context, Common.Status.CREATED, username, builder);
        } catch (Exception err) {
            log.error("Error on actionType [{}] at user_actor [{}] :: [{}]", actionType, username, err.getMessage());
            log.trace("Error on actionType [{}] at user_actor [{}].", actionType, username, err);
            return buildValueWith(context, Common.Status.ERROR, username, builder);
        }
    }

    private boolean checkIfProcessAlreadyDone(ActorContext<User.UserState> context) {
        if (context.getState().isPresent()) {
            log.debug("State is present and value is '{}'.", context.getState().get());
            if (Arrays.asList(Common.Status.FOUND, Common.Status.CREATED, Common.Status.UPDATED)
                    .contains(context.getState().get().getStatus())) {
                log.debug("User [{}] already processed.", context.getState().get().getUsername());
                return true;
            }
        }

        return false;
    }

    private Value buildValueWith(
            ActorContext<User.UserState> context,
            Common.Status status,
            String username,
            User.UserState.Builder builder) {

        log.debug("Build Value response with status [{}] from Actor [{}]", status, username);
        ActorRef reportActorRef = null;
        try {
            reportActorRef = context.getSpawnSystem()
                    .createActorRef(ActorIdentity.of(context.getSpawnSystem().getSystem(),
                            "report_actor"));
        } catch (ActorCreationException err) {
            log.error("Error on create report_actor {}.", username, err);
        }

        log.debug("Report Actor SideEffect reference: [{}]", reportActorRef);
        Value value = Value.at()
                .flow(SideEffect.to(
                        reportActorRef,
                        "summarize",
                        Report.DataPointRequest.newBuilder()
                                .setUsername(username)
                                .setStatus(status)
                                .build()))
                .state(builder
                        .setStatus(status)
                        .build())
                .noReply();

        log.debug("Send behaviour to proxy. Value: {}", value);
        return value;
    }

    @TimerAction(period = 60000)
    public Value retry(ActorContext<User.UserState> context) throws ActorCreationException {
        if (context.getState().isPresent()) {
            log.debug("Check retry user [{}].", context.getState().get().getUsername());
            if (Arrays.asList(Common.Status.UNKNOWN, Common.Status.ERROR).contains(context.getState().get().getStatus())) {
                log.trace("Retrying user [{}].", context.getState().get().getUsername());
                ActorRef actorRef = makeRef(context, context.getState().get().getUsername());
                User.UserRequest userRequest = User.UserRequest.newBuilder()
                        .setState(context.getState().get())
                        .build();
                return Value.at()
                        .flow(SideEffect.to(actorRef, "createOrUpdateIdentity", userRequest))
                        .noReply();
            }
        } else {
            log.warn("Retry without state!");
        }
        return Value.at().noReply();
    }

    private ActorRef makeRef(ActorContext<User.UserState> context, String user) throws ActorCreationException {
        ActorIdentity self = ActorIdentity.of(context.getSpawnSystem().getSystem(), user,
                "user_actor");

        return context.getSpawnSystem().createActorRef(self);
    }

    private void setUserAttributes(User.UserRequest msg, UserKeycloakRepresentation userRepresentation, AtomicBoolean update) {
        if (Objects.isNull(userRepresentation.getAttributes())) {
            userRepresentation.setAttributes(new HashMap<>());
        }
        if (!userRepresentation.getAttributes().containsKey("birthdate") && !msg.getState().getBirthDate().isEmpty()) {
            userRepresentation.getAttributes().put("birthdate", new String[]{msg.getState().getBirthDate()});
            update.set(true);
        }
        if (!userRepresentation.getAttributes().containsKey("ibgecity") && !msg.getState().getCityIbgeId().isEmpty()) {
            userRepresentation.getAttributes().put("ibgecity", new String[]{msg.getState().getCityIbgeId()});
            update.set(true);
        }
        if (!userRepresentation.getAttributes().containsKey("mobile") && !msg.getState().getMobile().isEmpty()) {
            userRepresentation.getAttributes().put("mobile", new String[]{msg.getState().getMobile()});
            update.set(true);
        }
        if (!userRepresentation.getAttributes().containsKey("region") && !msg.getState().getUf().isEmpty()) {
            userRepresentation.getAttributes().put("region", new String[]{msg.getState().getUf()});
            update.set(true);
        }

    }
}