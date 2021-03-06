package io.yodata.ldp.solid.server.model.event;

import io.yodata.GsonUtil;
import io.yodata.ldp.solid.server.MimeTypes;
import io.yodata.ldp.solid.server.model.Request;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public abstract class SkeletonEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(SkeletonEventBus.class);

    public void sendStoreEvent(Request in) {
        StorageAction event = new StorageAction();
        event.setTarget(in.getTarget().getId().toString());
        event.setId(in.getDestination().getId().toString());
        switch (in.getMethod()) {
            case "POST":
                event.setType(StorageAction.Add);
                break;
            case "PUT":
                event.setType(StorageAction.Update);
                break;
            case "DELETE":
                event.setType(StorageAction.Delete);
                break;
            default:
                log.info("HTTP method {} is unknown, cannot send storage action event", in.getMethod());
                return;
        }

        in.getContentType().filter(ct -> StringUtils.equals(MimeTypes.APPLICATION_JSON, ct)).ifPresent(ct -> {
            try {
                event.setObject(GsonUtil.parseObj(in.getBody()));
            } catch (IllegalArgumentException e) {
                log.info("JSON was given but could not be parsed as JSON object, not adding to the body");
            }
        });

        in.setBody("".getBytes(StandardCharsets.UTF_8));
        event.setRequest(in);

        doSend(event);
    }

    protected abstract void doSend(StorageAction msg);

}
