package io.yodata.ldp.solid.server.undertow.handler;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.yodata.GsonUtil;
import io.yodata.ldp.solid.server.exception.EncodingNotSupportedException;
import io.yodata.ldp.solid.server.exception.ForbiddenException;
import io.yodata.ldp.solid.server.exception.NotFoundException;
import io.yodata.ldp.solid.server.exception.UnauthorizedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionHandler extends BasicHttpHandler {

    private final transient Logger log = LoggerFactory.getLogger(ExceptionHandler.class);

    private static final String CorsOriginName = "Access-Control-Allow-Origin";
    private static final String CorsOriginValue = "*";
    private static final String CorsMethodsName = "Access-Control-Allow-Methods";
    private static final String CorsMethodsValue = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String CorsHeadersName = "Access-Control-Allow-Headers";
    private static final String CorsHeadersValue = "*";

    private HttpHandler h;

    public ExceptionHandler(HttpHandler h) {
        this.h = h;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            log.info("HTTP Request {}: Start", exchange.hashCode());

            putHeader(exchange, CorsOriginName, StringUtils.defaultIfBlank(exchange.getRequestHeaders().getFirst("Origin"), "*"));
            putHeader(exchange, "Access-Control-Allow-Credentials", "true");
            putHeader(exchange, CorsMethodsName, CorsMethodsValue);
            putHeader(exchange, CorsHeadersName, StringUtils.defaultIfBlank(exchange.getRequestHeaders().getFirst("Access-Control-Request-Headers"), "*"));

            h.handleRequest(exchange);
        } catch (IllegalArgumentException e) {
            writeBody(exchange, 400, GsonUtil.makeObj("error", e.getMessage()));
        } catch (UnauthorizedException e) {
            writeBody(exchange, 401, GsonUtil.makeObj("error", e.getMessage()));
        } catch (ForbiddenException e) {
            writeBody(exchange, 403, GsonUtil.makeObj("error", e.getMessage()));
        } catch (NotFoundException e) {
            writeBody(exchange, 404, GsonUtil.makeObj("error", e.getMessage()));
        } catch (EncodingNotSupportedException e) {
            writeBody(exchange, 415, GsonUtil.makeObj("error", e.getMessage()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            writeBody(exchange, 500, GsonUtil.makeObj("error", "An internal server occurred"));
        } finally {
            exchange.endExchange();
            log.info("HTTP Request {}: End", exchange.hashCode());
        }
    }

}
