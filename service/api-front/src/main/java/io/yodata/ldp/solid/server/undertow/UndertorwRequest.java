package io.yodata.ldp.solid.server.undertow;

import io.undertow.server.HttpServerExchange;
import io.yodata.ldp.solid.server.model.Acl;
import io.yodata.ldp.solid.server.model.Request;
import io.yodata.ldp.solid.server.model.SecurityContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class UndertorwRequest extends Request {

    private static byte[] getBody(HttpServerExchange ex) {
        if (ex.getRequestContentLength() < 1) {
            return new byte[0];
        }

        try {
            return IOUtils.toByteArray(ex.getInputStream(), ex.getRequestContentLength());
        } catch (IOException e) {
            throw new RuntimeException("Error while fetching request body", e);
        }
    }

    public static String getBodyString(HttpServerExchange ex) {
        return StringUtils.toEncodedString(getBody(ex), StandardCharsets.UTF_8);
    }

    public static UndertorwRequest build(
            HttpServerExchange ex,
            SecurityContext sc,
            UndertowTarget target,
            Acl acl,
            Map<String, List<String>> headers
    ) {
        UndertorwRequest r = new UndertorwRequest();

        r.setId(UUID.randomUUID().toString());
        r.setTimestamp(Instant.now());
        r.setSecurity(sc);
        r.setTarget(target);
        r.setAcl(acl);
        r.setMethod(ex.getRequestMethod().toString());
        r.setHeaders(new HashMap<>(headers));
        r.setParameters(new HashMap<>());

        ex.getQueryParameters().forEach((key, queue) -> queue.forEach(value -> {
            r.parameters.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }));

        r.setBody(getBody(ex));

        return r;
    }

}
