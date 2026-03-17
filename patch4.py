import re
path = "/Users/samujjwal/Development/ghatana/products/app-platform/kernel/event-store/src/test/java/com/ghatana/appplatform/eventstore/saga/SagaTimeoutMonitorTest.java"
with open(path, "r") as f:
    text = f.read()

text = text.replace("import static org.mockito.Mockito.*;", "import static org.mockito.Mockito.*;\nimport io.activej.eventloop.Eventloop;\nimport java.util.concurrent.Executors;")
text = re.sub(r'monitor = new SagaTimeoutMonitor\(sagaStore, orchestrator,\n\s*Duration\.ofMinutes\(5\), Duration\.ofSeconds\((30|60)\)\);',
              r'monitor = new SagaTimeoutMonitor(sagaStore, orchestrator, Duration.ofMinutes(5), Duration.ofSeconds(\1), Eventloop.create(), Executors.newSingleThreadExecutor());', text)

with open(path, "w") as f:
    f.write(text)
