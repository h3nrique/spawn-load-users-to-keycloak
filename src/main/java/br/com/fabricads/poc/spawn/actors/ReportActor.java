package br.com.fabricads.poc.spawn.actors;

import br.com.fabricads.poc.proto.Report;
import io.eigr.spawn.api.actors.ActorContext;
import io.eigr.spawn.api.actors.Value;
import io.eigr.spawn.api.actors.annotations.Action;
import io.eigr.spawn.api.actors.annotations.stateful.StatefulNamedActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@StatefulNamedActor(name = "report_actor", stateType = Report.ReportState.class)
public final class ReportActor {

    private static final Logger log = LoggerFactory.getLogger(ReportActor.class);

    @Action
    public Value summarize(Report.DataPointRequest datapoint, ActorContext<Report.ReportState> context) {
        log.debug("[ReportActor] Received invocation. Message: '{}'. Context: '{}'.", datapoint, context);
        String username = datapoint.getUsername();

        Report.ReportState.Builder reportBuilder = initBuilderReport(context);

        switch (datapoint.getStatus()) {
            case ERROR, CONFLICT -> {
                Report.ErrorsView.Builder usersWithErrors = reportBuilder.getUsersWithErrors().toBuilder();
                boolean contains = usersWithErrors.getUsersList().contains(username);
                log.debug("[ReportActor] Report Error. User [{}] already exists in error list? :: [{}]", username, contains);
                if (!contains) {
                    List<String> users = Collections.singletonList(username);
                    reportBuilder.setUsersWithErrors(usersWithErrors.addAllUsers(users).build());
                    reportBuilder.setCounters(
                            reportBuilder.getCounters()
                                    .toBuilder()
                                    .setErrors(usersWithErrors.getUsersList().size())
                                    .build());
                }
            }
            default -> {
                var successCounter = reportBuilder.getCounters().getSuccess() + 1; // thread safe because an Actor is thread safe
                Report.ErrorsView.Builder usersWithErrors = reportBuilder.getUsersWithErrors().toBuilder();

                boolean contains = usersWithErrors.getUsersList().contains(username);
                log.debug("[ReportActor] Report Success. User [{}] already exists in error list? :: [{}]", username, contains);
                if (contains) {
                    List<String> newUserList = usersWithErrors.getUsersList()
                            .stream()
                            .filter(user -> !user.equals(username))
                            .collect(Collectors.toList());

                    reportBuilder.setUsersWithErrors(Report.ErrorsView.newBuilder()
                            .addAllUsers(newUserList).build());
                    reportBuilder.setCounters(
                            reportBuilder.getCounters()
                                    .toBuilder()
                                    .setErrors(newUserList.size())
                                    .setSuccess(successCounter)
                                    .build());
                } else {
                    reportBuilder.setCounters(
                            reportBuilder.getCounters()
                                    .toBuilder()
                                    .setSuccess(successCounter)
                                    .build());
                }
            }
        }

        Report.ReportState state = reportBuilder.build();
        log.debug("[ReportActor] Actual state: {}", state);
        return Value.at()
                .state(state)
                .noReply();
    }

    private Report.ReportState.Builder initBuilderReport(ActorContext<Report.ReportState> context) {
        Report.ReportState.Builder reportBuilder = Report.ReportState.newBuilder();
        if (context.getState().isEmpty()) {
            log.error("State not present. Building default state...");
            Report.StatusCounters counters = Report.StatusCounters.newBuilder().build();
            Report.ErrorsView errorsView = Report.ErrorsView.newBuilder()
                    .build();
            reportBuilder.setCounters(counters);
            reportBuilder.setUsersWithErrors(errorsView);
        } else {
            reportBuilder = context.getState().get().toBuilder();
        }
        return reportBuilder;
    }
}