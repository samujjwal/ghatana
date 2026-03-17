import re
path = "/Users/samujjwal/Development/ghatana/products/app-platform/service-template/src/test/java/com/ghatana/appplatform/template/KernelServiceTemplateTest.java"
with open(path, "r") as f:
    text = f.read()

text = re.sub(
    r'private io\.activej\.promise\.Promise<HttpResponse> httpGet\(String path\) \{[^}]+\}',
    """private io.activej.promise.Promise<HttpResponse> httpGet(String path) {
        return io.activej.promise.Promise.ofBlocking(java.util.concurrent.Executors.newSingleThreadExecutor(), () -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + service.getPort() + path))
                    .GET()
                    .build();
                java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                return HttpResponse.ofCode(res.statusCode()).withBody(res.body().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }""", text)

with open(path, "w") as f:
    f.write(text)
