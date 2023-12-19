package br.com.fabricads.poc.spawn.handler;

import br.com.fabricads.poc.proto.Common;
import br.com.fabricads.poc.proto.Report;
import br.com.fabricads.poc.proto.User;
import br.com.fabricads.poc.spawn.RestServer;
import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import io.eigr.spawn.api.ActorIdentity;
import io.eigr.spawn.api.ActorRef;
import io.eigr.spawn.api.Spawn;
import io.eigr.spawn.api.exceptions.SpawnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ImportHandler extends FormDataHandler {

    private static final Logger log = LoggerFactory.getLogger(RestServer.class);
    private static final Pattern getEndpointPattern = Pattern.compile("/import/status/([0-9]{11})");
    private static final Pattern importEndpointPattern = Pattern.compile("/import");
    private static final Pattern cpfPattern = Pattern.compile("^[0-9]{11}$");
    private static final Map<String, String> defaultHeaders = new HashMap<>() {{
        put("Access-Control-Allow-Methods", "*");
        put("Access-Control-Allow-Origin", "*");
    }};

    private final Spawn spawn;

    public ImportHandler(Spawn spawn) {
        this.spawn = spawn;
    }

    @Override
    public void handle(HttpExchange exchange, List<MultiPart> parts) throws IOException {
        switch (exchange.getRequestMethod()) {
            case "POST":
                postRequest(exchange, parts);
                break;
            case "GET":
                getRequest(exchange);
                break;
            case "OPTIONS":
                optionsRequest(exchange);
                break;
            default:
                otherwiseRequest(exchange);
                break;
        }
    }

    private void getRequest(HttpExchange exchange) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            String path = exchange.getRequestURI().getPath();
            Matcher getUsernameMatcher = getEndpointPattern.matcher(path);
            Matcher listPostalCodeMatcher = importEndpointPattern.matcher(path);
            if (listPostalCodeMatcher.matches()) {
                ActorRef userGenerator = spawn.createActorRef( ActorIdentity.of(spawn.getSystem(), "report_actor"));
                Optional<Report.ReportState> reportState = userGenerator.invoke("get", Report.ReportState.class);
                if(reportState.isPresent()) {
                    log.debug("Report [{}]", reportState.get());
                    byte[] bytes = JsonFormat.printer().print(reportState.get()).getBytes();
                    defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    out.write(bytes);
                } else {
                    log.info("Sem status.");
                    defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(204, 0L);
                    out.write("".getBytes());
                }
            } else if (getUsernameMatcher.matches()) {
                String username = getUsernameMatcher.group(1);
                username = username.substring(0, 3) + "." + username.substring(3, 6) +
                        "." + username.substring(6, 9) + "-" + username.substring(9, 11);
                ActorRef actorRef = spawn
                        .createActorRef(ActorIdentity.of(spawn.getSystem(), username, "user_actor"));
                Optional<User.UserState> actorState = actorRef.invoke("get", User.UserState.class);
                if (actorState.isPresent()) {
                    byte[] bytes = JsonFormat.printer().print(actorState.get()).getBytes();
                    defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    out.write(bytes);
                } else {
                    byte[] bytes = "{\"error\": \"User not found\"}".getBytes();
                    defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, bytes.length);
                    out.write(bytes);
                }
            } else {
                badRequest(exchange, out);
            }
        } catch (IOException | SpawnException err) {
            log.error("Internal Error.", err);
            internalError(exchange);
        }
    }

    private void postRequest(HttpExchange exchange, List<MultiPart> parts) throws IOException {
        try (OutputStream out = exchange.getResponseBody(); InputStream in = exchange.getRequestBody()) {
            String path = exchange.getRequestURI().getPath();
            Matcher createPostalCodeMatcher = importEndpointPattern.matcher(path);
            if(createPostalCodeMatcher.matches() && exchange.getRequestHeaders().containsKey("Content-type")
                    && exchange.getRequestHeaders().getFirst("Content-type").startsWith("multipart/form-data")) {
                if(parts == null || parts.isEmpty()) {
                    badRequest(exchange, out);
                    return;
                }
                ActorRef userGenerator = spawn.createActorRef( ActorIdentity.of(spawn.getSystem(), "csv_parser_generator"));
                Common.GeneratorRequest msg = Common.GeneratorRequest.newBuilder()
                        .setCsvContent(new String(parts.get(0).bytes))
                        .build();
                userGenerator.invokeAsync("generate", msg);
                defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, 0L);
                out.write("".getBytes());
                return;
            } else if(createPostalCodeMatcher.matches() && exchange.getRequestHeaders().containsKey("Content-type")
                    && exchange.getRequestHeaders().getFirst("Content-type").startsWith("application/json")) {
                String requestBody = new BufferedReader(new InputStreamReader(in))
                        .lines()
                        .collect(Collectors.joining("\n"));
                log.debug("requestBody :: {}", requestBody);
                User.UserState.Builder userStateBuilder = User.UserState.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(requestBody, userStateBuilder);

                if(cpfPattern.matcher(userStateBuilder.getUsername()).matches()) {
                    String username = userStateBuilder.getUsername().substring(0, 3) + "." +
                            userStateBuilder.getUsername().substring(3, 6) + "." +
                            userStateBuilder.getUsername().substring(6, 9) + "-" +
                            userStateBuilder.getUsername().substring(9, 11);
                    userStateBuilder.setUsername(username);
                    ActorIdentity actorIdentity = ActorIdentity.of(spawn.getSystem(), username, "user_actor");
                    spawn.createActorRef(actorIdentity)
                            .invokeAsync("onCreate", User.UserRequest.newBuilder()
                                    .setState(userStateBuilder.build())
                                    .build());
                    defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, 0L);
                    out.write("".getBytes());
                    return;
                }
            }
            badRequest(exchange, out);
        } catch (SpawnException err) {
            log.error("Actor invocation error.", err);
            internalError(exchange);
        } catch (IOException err) {
            log.error("Internal Error.", err);
            internalError(exchange);
        }
    }

    private void optionsRequest(HttpExchange exchange) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
            exchange.sendResponseHeaders(200, 0);
            out.write("".getBytes());
        } catch (IOException err) {
            log.error("Internal Error.", err);
            internalError(exchange);
        }
    }

    private void otherwiseRequest(HttpExchange exchange) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            byte[] bytes = "{\"error\": \"Invalid request\"}".getBytes();
            defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, bytes.length);
            out.write(bytes);
        } catch (IOException err) {
            log.error("Internal Error.", err);
            internalError(exchange);
        }
    }

    private static void internalError(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(500, 0);
    }

    private static void badRequest(HttpExchange exchange, OutputStream out) throws IOException {
        out = out == null ? exchange.getResponseBody() : out;
        byte[] bytes = "{\"error\": \"Bad Request\"}".getBytes();
        defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(400, bytes.length);
        out.write(bytes);
    }

    private static void notImplementedYet(HttpExchange exchange) throws IOException {
        notImplementedYet(exchange, null);
    }

    private static void notImplementedYet(HttpExchange exchange, OutputStream out) throws IOException {
        byte[] bytes = "{\"error\": \"Not Implemented Yet\"}".getBytes();
        defaultHeaders.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(400, bytes.length);
        if(out == null) {
            exchange.getResponseBody().write(bytes);
        } else {
            out.write(bytes);
        }
    }
    public Map<String, String> convertStringArrayToMap(String data) {
        Map<String, String> map = new HashMap<>();
        if(data == null) {
            return map;
        }
        for (String keyValue : data.split("&")) {
            String[] parts = keyValue.split("=");
            map.put(parts[0], parts[1]);
        }

        return map;
    }
}
