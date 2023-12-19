package br.com.fabricads.poc.spawn.actors;

import br.com.fabricads.poc.proto.Common;
import br.com.fabricads.poc.proto.User;
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

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;

@StatelessNamedActor(name = "csv_parser_generator")
public final class UsersGeneratorActor {

    private static final Logger log = LoggerFactory.getLogger(UsersGeneratorActor.class);

    private final App.Config cfg;

    public UsersGeneratorActor(DependencyInjector injector) {
        this.cfg = injector.getInstance(App.Config.class);
    }

    @Action(name = "generate")
    public Value generate(Common.GeneratorRequest msg, ActorContext<?> context) {
        log.debug("Received invocation. Message: '{}'. Context: '{}'.", msg, context);

        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger throughputCounter = new AtomicInteger(0);
        try (BufferedReader br =new BufferedReader(new StringReader(msg.getCsvContent()))) {
            String line;
            while ((line = br.readLine()) != null) {
                counter.getAndIncrement();
                if (isSourceDataInvalid(line)) {
                    continue;
                }

                if(cfg.backpressureLimit() != null && cfg.backpressurePause() != null) {
                    if (throughputCounter.getAndIncrement() == cfg.backpressureLimit()) {
                        throughputCounter.set(0);
                        Thread.sleep(cfg.backpressurePause());
                    }
                }

                createUserActor(context, line);
            }
        } catch (SpawnException er) {
            log.error("Error while spawning Actor.", er);
        } catch (Exception err) {
            log.error("Error parsing csv.", err);
        }

        log.debug("OK finished generation. Total lines in file [{}]", counter.get());
        return Value.at().noReply();
    }

    private void createUserActor(ActorContext<?> context, String line) throws ActorInvocationException, ActorCreationException {
        User.UserState userData = UserActorParser.parse(line);

        ActorIdentity actorIdentity = ActorIdentity.of(context.getSpawnSystem().getSystem(),
                userData.getUsername(), "user_actor");

        User.UserRequest userRequest = User.UserRequest.newBuilder()
                .setState(userData)
                .build();

        context.getSpawnSystem()
                .createActorRef(actorIdentity)
                .invokeAsync("onCreate", userRequest);
    }

    private boolean isSourceDataInvalid(String line) {
        String[] values = line.split(",");
        if (values.length < 4 || values[1].length() != 11) {
            log.warn("Invalid user [{}]", line);
            return true;
        }

        return false;
    }

    static class UserActorParser {

        public static User.UserState parse(String userText) {
            String[] values = userText.split(",");

            log.trace("CsvUser [{}]", userText);
            String username = values[1];
            String email = values[0];
            String firstName = values[2];
            String lastName = values[3];
            String birthDate = values.length > 4 ? values[4] : "";
            String cityIbgeId = values.length > 7 ? values[7] : "";
            String mobile = values.length > 5 ? values[5] : "";
            String uf = values.length > 8 ? values[8] : "";

            username = username.substring(0, 3) + "." + username.substring(3, 6) +
                    "." + username.substring(6, 9) + "-" + username.substring(9, 11);

            return User.UserState.newBuilder()
                    .setUsername(username)
                    .setBirthDate(birthDate)
                    .setEmail(email)
                    .setCityIbgeId(cityIbgeId)
                    .setFirstName(firstName)
                    .setLastName(lastName)
                    .setUf(uf)
                    .setMobile(mobile)
                    .build();
        }
    }
}