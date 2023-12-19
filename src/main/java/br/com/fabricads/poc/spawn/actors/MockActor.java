package br.com.fabricads.poc.spawn.actors;

import br.com.fabricads.poc.proto.Mock;
import br.com.fabricads.poc.spawn.services.MockService;
import io.eigr.spawn.api.ActorIdentity;
import io.eigr.spawn.api.ActorRef;
import io.eigr.spawn.api.actors.ActorContext;
import io.eigr.spawn.api.actors.Value;
import io.eigr.spawn.api.actors.annotations.Action;
import io.eigr.spawn.api.actors.annotations.stateful.StatefulUnNamedActor;
import io.eigr.spawn.api.actors.workflows.SideEffect;
import io.eigr.spawn.api.exceptions.ActorCreationException;
import io.eigr.spawn.api.extensions.DependencyInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@StatefulUnNamedActor(name = "mock_actor", stateType = Mock.MockState.class, deactivatedTimeout = 3600000L)
public final class MockActor {

    private static final Logger log = LoggerFactory.getLogger(MockActor.class);

    private final MockService mockService;

    public MockActor(DependencyInjector injector) {
        this.mockService = injector.getInstance(MockService.class);
    }

    @Action
    public Value onCreate(Mock.MockRequest msg, ActorContext<Mock.MockState> context) throws ActorCreationException {
        ActorRef actorRef = makeRef(context, msg.getState().getUsername());
        return Value.at()
                .state(msg.getState())
                .flow(SideEffect.to(actorRef, "createOrUpdateIdentity", msg))
                .noReply();
    }

    @Action
    public Value createOrUpdateIdentity(Mock.MockRequest msg, ActorContext<Mock.MockState> context) {
        log.debug("Received invocation. Message: '{}'. Context: '{}'.", msg, context);
        String username = msg.getState().getUsername();
        try {
            log.debug("Checking if user [{}] exists", msg.getState().getUsername());
            Optional<Long> counterOptional = mockService.counter();
            if (counterOptional.isPresent()) {
                log.debug("User [{}] found", msg.getState().getUsername());
                return Value.at().noReply();
            }
            log.debug("User [{}] not found", msg.getState().getUsername());
            return Value.at().noReply();
        } catch (Exception err) {
            log.error("Error at mock_actor [{}] :: [{}]", username, err.getMessage());
            log.trace("Error at mock_actor [{}].", username, err);
            return Value.at().noReply();
        }
    }

    private ActorRef makeRef(ActorContext<Mock.MockState> context, String user) throws ActorCreationException {
        ActorIdentity self = ActorIdentity.of(context.getSpawnSystem().getSystem(), user,
                "mock_actor");

        return context.getSpawnSystem().createActorRef(self);
    }
}