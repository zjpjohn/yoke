package com.jetdrone.vertx.yoke.extras.middleware;

import com.jetdrone.vertx.yoke.Middleware;
import com.jetdrone.vertx.yoke.Yoke;
import com.jetdrone.vertx.yoke.middleware.YokeRequest;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: add api_key validator
// TODO: remove yoke as arg, use router? maybe?
public class Swagger extends Middleware {

    public static class Resource {

        final String path;
        final String description;

        JsonArray produces;
        JsonArray consumes;

        JsonObject models = new JsonObject();
        JsonObject apis = new JsonObject();

        Resource(String path, String description) {
            this.path = path;
            this.description = description;
        }

        private JsonObject getApi(String path) {
            JsonObject api = apis.getObject(path);
            if (api == null) {
                api = new JsonObject()
                        .putString("path", path)
                        .putArray("operations", new JsonArray());

                apis.putObject(path, api);
            }
            return api;
        }

        public Resource produces(String... mimes) {
            produces = new JsonArray();
            for (String mime : mimes) {
                produces.addString(mime);
            }

            return this;
        }

        public Resource consumes(String... mimes) {
            consumes = new JsonArray();
            for (String mime : mimes) {
                consumes.addString(mime);
            }

            return this;
        }

        public Resource get(String path, JsonObject operation) {
            get(path, "", operation);
            return this;
        }
        public Resource get(String path, String summary, JsonObject operation) {
            addOperation("GET", path, summary, operation);
            return this;
        }
        public Resource post(String path, JsonObject operation) {
            post(path, "", operation);
            return this;
        }
        public Resource post(String path, String summary, JsonObject operation) {
            addOperation("POST", path, summary, operation);
            return this;
        }
        public Resource put(String path, JsonObject operation) {
            put(path, "", operation);
            return this;
        }
        public Resource put(String path, String summary, JsonObject operation) {
            addOperation("PUT", path, summary, operation);
            return this;
        }
        public Resource delete(String path, JsonObject operation) {
            delete(path, "", operation);
            return this;
        }
        public Resource delete(String path, String summary, JsonObject operation) {
            addOperation("DELETE", path, summary, operation);
            return this;
        }
        public Resource options(String path, JsonObject operation) {
            options(path, "", operation);
            return this;
        }
        public Resource options(String path, String summary, JsonObject operation) {
            addOperation("OPTIONS", path, summary, operation);
            return this;
        }
        public Resource head(String path, JsonObject operation) {
            head(path, "", operation);
            return this;
        }
        public Resource head(String path, String summary, JsonObject operation) {
            addOperation("HEAD", path, summary, operation);
            return this;
        }
        public Resource trace(String path, JsonObject operation) {
            trace(path, "", operation);
            return this;
        }
        public Resource trace(String path, String summary, JsonObject operation) {
            addOperation("TRACE", path, summary, operation);
            return this;
        }
        public Resource connect(String path, JsonObject operation) {
            connect(path, "", operation);
            return this;
        }
        public Resource connect(String path, String summary, JsonObject operation) {
            addOperation("CONNECT", path, summary, operation);
            return this;
        }
        public Resource patch(String path, JsonObject operation) {
            patch(path, "", operation);
            return this;
        }
        public Resource patch(String path, String summary, JsonObject operation) {
            addOperation("PATCH", path, summary, operation);
            return this;
        }

        public Resource addModel(String name, JsonObject model) {
            model.putString("id", name);
            models.putObject(name, model);
            return this;
        }

        protected void addOperation(String verb, String path, String summary, JsonObject operation) {
            // translate from yoke format to swagger format
            Matcher m =  Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(path);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, "\\{$1\\}");
            }
            m.appendTail(sb);

            operation.putString("method", verb);
            operation.putString("summary", summary);

            JsonObject api = getApi(sb.toString());
            api.getArray("operations").addObject(operation);
        }
    }

    private final List<Resource> resources = new ArrayList<>();

    private final String apiVersion;
    private JsonObject info;
    private JsonObject authorizations;

    public Swagger(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Swagger setInfo(JsonObject info) {
        this.info = info;
        return this;
    }

    public Swagger setAuthorizations(JsonObject authorizations) {
        this.authorizations = authorizations;
        return this;
    }

    private String getBasePath() {
        if ("/".equals(mount)) {
            return "/api-docs";
        } else {
            return mount + "/api-docs";
        }
    }

    protected Swagger.Resource createResource(String path, String description) {
        return new Swagger.Resource(path, description);
    }

    @Override
    public void handle(YokeRequest request, Handler<Object> next) {
        String path = getBasePath();

        if (path.equals(request.normalizedPath())) {
            if ("GET".equals(request.method())) {
                JsonObject result = new JsonObject()
                        .putString("apiVersion", apiVersion)
                        .putString("swaggerVersion", "1.2");

                JsonArray apis = new JsonArray();
                result.putArray("apis", apis);

                for (Resource r : resources) {
                    apis.addObject(new JsonObject()
                            .putString("path", r.path)
                            .putString("description", r.description));
                }

                if (authorizations != null) {
                    result.putObject("authorizations", authorizations);
                }

                result.putObject("info", info);

                request.response().end(result);
            } else {
                next.handle(405);
            }
        } else {
            next.handle(null);
        }
    }

    public Swagger.Resource createResource(Yoke yoke, final String path) {
        return createResource(yoke, path, "");
    }

    public Swagger.Resource createResource(Yoke yoke, final String path, final String description) {

        final String normalizedPath;

        if (path.charAt(0) != '/') {
            normalizedPath = "/" + path;
        } else {
            normalizedPath = path;
        }

        final Swagger.Resource resource = createResource(normalizedPath, description);
        resources.add(resource);

        yoke.use(getBasePath() + normalizedPath, new Middleware() {
            @Override
            public void handle(YokeRequest request, Handler<Object> next) {
                if ("GET".equals(request.method())) {
                    if (!request.normalizedPath().equals(getBasePath() + normalizedPath)) {
                        next.handle(null);
                        return;
                    }

                    JsonObject result = new JsonObject()
                            .putString("apiVersion", apiVersion)
                            .putString("swaggerVersion", "1.2")
                            .putString("basePath", "/")
                            .putString("resourcePath", normalizedPath);

                    if (resource.produces != null) {
                        result.putArray("produces", resource.produces);
                    }

                    if (resource.consumes != null) {
                        result.putArray("consumes", resource.consumes);
                    }

                    JsonArray apis = new JsonArray();
                    result.putArray("apis", apis);

                    for (String key : resource.apis.getFieldNames()) {
                        apis.addObject(resource.apis.getObject(key));
                    }
                    result.putObject("models", resource.models);
                    request.response().end(result);
                } else {
                    next.handle(400);
                }
            }
        });

        return resource;
    }
}
