import re
path = "/Users/samujjwal/Development/ghatana/products/app-platform/service-template/src/test/java/com/ghatana/appplatform/template/KernelServiceTemplateTest.java"
with open(path, "r") as f:
    text = f.read()

# Change the method signature
text = re.sub(
    r'private io\.activej\.promise\.Promise<HttpResponse> httpGet',
    r'private io.activej.promise.Promise<java.net.http.HttpResponse<String>> httpGet',
    text
)

# Change HttpResponse.ofCode etc to just return `res`
text = re.sub(r'return HttpResponse\.ofCode.*?;', r'return res;', text)

# Change the tests consuming it
text = text.replace('HttpResponse::getCode', 'java.net.http.HttpResponse::statusCode')
text = text.replace('new String(r.getBody().getArray())', 'r.body()')

with open(path, "w") as f:
    f.write(text)
