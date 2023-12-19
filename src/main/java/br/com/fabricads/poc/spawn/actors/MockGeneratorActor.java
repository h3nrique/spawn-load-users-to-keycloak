package br.com.fabricads.poc.spawn.actors;

import br.com.fabricads.poc.proto.Mock;
import br.com.fabricads.poc.spawn.App;
import io.eigr.spawn.api.ActorIdentity;
import io.eigr.spawn.api.actors.ActorContext;
import io.eigr.spawn.api.actors.Value;
import io.eigr.spawn.api.actors.annotations.Action;
import io.eigr.spawn.api.actors.annotations.stateless.StatelessNamedActor;
import io.eigr.spawn.api.exceptions.ActorCreationException;
import io.eigr.spawn.api.exceptions.ActorInvocationException;
import io.eigr.spawn.api.exceptions.SpawnException;
import io.eigr.spawn.api.extensions.DependencyInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@StatelessNamedActor(name = "mock_generator")
public final class MockGeneratorActor {

    private static final Logger log = LoggerFactory.getLogger(MockGeneratorActor.class);

    private final App.Config cfg;

    public MockGeneratorActor(DependencyInjector injector) {
        this.cfg = injector.getInstance(App.Config.class);
    }

    @Action(name = "generate")
    public Value generate(Mock.MockGeneratorRequest msg, ActorContext<?> context) {
        log.debug("Received invocation. Message: '{}'. Context: '{}'.", msg, context);

        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger throughputCounter = new AtomicInteger(0);
        try {
            while (counter.get() < msg.getQdte()) {
                counter.getAndIncrement();
                if(cfg.backpressureLimit() != null && cfg.backpressurePause() != null) {
                    if (throughputCounter.getAndIncrement() == cfg.backpressureLimit()) {
                        throughputCounter.set(0);
                        Thread.sleep(cfg.backpressurePause());
                    }
                }

                createMockActor(context);
            }
        } catch (SpawnException er) {
            log.error("Error while spawning Actor.", er);
        } catch (Exception err) {
            log.error("Error parsing csv.", err);
        }

        log.debug("OK finished generation. Total lines in file [{}]", counter.get());
        return Value.at().noReply();
    }

    private void createMockActor(ActorContext<?> context) throws ActorInvocationException, ActorCreationException {
        String username = UUID.randomUUID().toString();
        ActorIdentity actorIdentity = ActorIdentity.of(context.getSpawnSystem().getSystem(), username, "mock_actor");

        Mock.MockRequest userRequest = Mock.MockRequest.newBuilder()
                .setState(Mock.MockState.newBuilder()
                        .setUsername(username)
                        .build())
                .build();

        context.getSpawnSystem()
                .createActorRef(actorIdentity)
                .invokeAsync("onCreate", userRequest);
    }

}