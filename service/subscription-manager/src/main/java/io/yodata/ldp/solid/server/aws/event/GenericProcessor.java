package io.yodata.ldp.solid.server.aws.event;

import com.google.gson.JsonObject;
import io.yodata.GsonUtil;
import io.yodata.ldp.solid.server.aws.SqsPusher;
import io.yodata.ldp.solid.server.aws.transform.AWSTransformService;
import io.yodata.ldp.solid.server.model.*;
import io.yodata.ldp.solid.server.model.event.StorageAction;
import io.yodata.ldp.solid.server.model.transform.TransformMessage;
import io.yodata.ldp.solid.server.model.transform.TransformService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GenericProcessor {

    private static final Logger log = LoggerFactory.getLogger(GenericProcessor.class);

    private final SolidServer srv;
    private final TransformService transform;
    private final SqsPusher pusher;

    public GenericProcessor(SolidServer srv) {
        this.srv = srv;
        this.transform = new AWSTransformService();
        this.pusher = new SqsPusher();
    }

    public void handleEvent(JsonObject event) {
        StorageAction action = GsonUtil.get().fromJson(event, StorageAction.class);
        URI id = URI.create(StringUtils.defaultIfBlank(action.getId(), "https://fail.yodata.io/unknown/id"));
        URI target = URI.create(action.getTarget());
        log.info("Processing storage event {} about {}", action.getType(), action.getId());

        List<Subscription> subs = srv.store().getAllSubscriptions(id);
        if (subs.isEmpty()) {
            log.info("No subscription found");
            return;
        }
        log.info("Processing {} subscription(s)", subs.size());

        for (Subscription sub : subs) {
            process(sub, target, event.deepCopy());
        }

        log.info("All subscriptions processed");
    }

    public void process(Subscription sub, URI target, JsonObject event) {
        log.info("Processing subscription ID {} on target {}", sub.getId(), target.toString());

        if (StringUtils.isBlank(sub.getObject())) {
            log.warn("Object is blank, ignoring");
            return;
        }

        URI subObj;
        try {
            subObj = URI.create(sub.getObject());
        } catch (IllegalArgumentException e) {
            log.warn("Object \"{}\" is not a valid URI, skipping", sub.getObject());
            return;
        }

        String host = subObj.getHost();
        if (!StringUtils.isBlank(host)) {
            if (host.startsWith("*.")) {
                host = host.substring(1);
                if (!target.getHost().endsWith(host)) {
                    log.info("Subscription ID {} does not match the object host pattern, skipping", sub.getId());
                    return;
                }

                if (target.getHost().equalsIgnoreCase(subObj.getHost())) {
                    log.info("Subscription target host is the same as event source host. Not allowing with a wildcard subscription");
                    return;
                }
            } else {
                if (!StringUtils.equals(target.getHost(), host)) {
                    log.info("Subscription ID {} does not match the object host, skipping", sub.getId());
                    return;
                }
            }
        }

        if (!target.getPath().startsWith(subObj.getPath())) {
            log.info("Subscription ID {} does not match the object, skipping", sub.getId());
            return;
        }

        StorageAction action = GsonUtil.get().fromJson(event, StorageAction.class);
        URI id = URI.create(StringUtils.defaultIfBlank(action.getId(), "https://fail.yodata.io/unknown/id"));

        if (!action.getObject().isPresent()) {
            log.info("Object is not present in the event: Fetching data from store to process");
            Optional<String> obj = srv.store().findEntityData(id, id.getPath());
            if (!obj.isPresent()) {
                log.info("We got a notification about {} which doesn't exist anymore, setting empty object", id);
                action.setObject(new JsonObject());
            } else {
                action.setObject(GsonUtil.parseObj(obj.get()));
            }
        }

        if (Objects.nonNull(sub.getScope())) {
            log.info("Subscription has a scope, processing");
            TransformMessage msg = new TransformMessage();
            msg.setSecurity(action.getRequest().getSecurity());
            msg.setScope(sub.getScope());
            msg.setPolicy(srv.store().getPolicies(id));
            msg.setObject(action.getObject().get());
            JsonObject rawData = transform.transform(msg);
            if (rawData.keySet().isEmpty()) {
                log.info("Sub {}: Transform removed all data, not sending notification", sub.getId());
                return;
            }

            action.setObject(rawData);
        }
        log.info("Data after scope: {}", GsonUtil.toJson(action));

        if (!StringUtils.isEmpty(sub.getAgent())) {
            log.info("Subscription is external");

            if (!action.getObject().isPresent()) {
                log.info("No content to send, skipping sub");
                return;
            }

            if (target.getPath().startsWith("/event/")) {
                // This is the event bus container, we consider the message final
                JsonObject msg = action.getObject().get();
                msg.addProperty("@to", sub.getAgent());

                // We build the store request
                Request r = Request.post().internal();
                r.setTarget(Target.forPath(new Target(id), "/outbox/"));
                r.setBody(msg);

                // We send to store
                Response res = srv.post(r);
                String eventId = GsonUtil.parseObj(res.getBody()
                        .orElse("{\"id\":\"<NOT RETURNED>\"".getBytes(StandardCharsets.UTF_8))).get("id").getAsString();
                log.info("Data was saved at {}", eventId);
            } else {
                // We rebuild the storage action to be sure only specific fields are there
                JsonObject actionNew = new JsonObject();
                actionNew.addProperty(ActionPropertyKey.Type.getId(), action.getType());
                actionNew.addProperty(ActionPropertyKey.Timestamp.getId(), Instant.now().toEpochMilli());
                actionNew.addProperty(ActionPropertyKey.Instrument.getId(), action.getRequest().getSecurity().getInstrument());
                action.getRequest().getSecurity().getAgent().ifPresent(a -> actionNew.addProperty(ActionPropertyKey.Agent.getId(), a));
                if (action.getObject().isPresent()) {
                    actionNew.add(ActionPropertyKey.Object.getId(), action.getObject().get());
                } else {
                    actionNew.addProperty(ActionPropertyKey.Object.getId(), action.getId());
                }

                // We notify about the event - Notifier will handle the wrapping and routing for us
                JsonObject publication = new JsonObject();
                publication.add("recipient", GsonUtil.asArray(sub.getAgent()));
                publication.add("payload", actionNew);

                // We build the store request
                Request r = Request.post().internal();
                r.setTarget(Target.forPath(new Target(id), "/notify/"));
                r.setBody(publication);

                // We send to store
                Response res = srv.post(r);
                String eventId = GsonUtil.parseObj(res.getBody()
                        .orElse("{\"id\":\"<NOT RETURNED>\"".getBytes(StandardCharsets.UTF_8))).get("id").getAsString();
                log.info("Data was saved at {}", eventId);
            }
        } else {
            log.info("Subscription is internal");

            JsonObject notification = new JsonObject();
            if (sub.needsContext()) {
                log.info("Context is needed, sending full version to endpoint");
                notification = GsonUtil.get().toJsonTree(action).getAsJsonObject();
            } else {
                log.info("Context is not needed, sending content only");

                if (action.getObject().isPresent()) {
                    notification = action.getObject().get();
                } else {
                    log.warn("No content, sending empty object");
                }
            }

            pusher.send(notification, sub.getTarget(), sub.getConfig());
        }
    }

}
