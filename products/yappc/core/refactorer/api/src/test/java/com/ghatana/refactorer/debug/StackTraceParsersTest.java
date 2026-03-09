package com.ghatana.refactorer.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles stack trace parsers test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class StackTraceParsersTest {
    @Test
    void javaParser_extractsFrames() {
        String trace =
                "java.lang.NullPointerException\n"
                        + "\tat com.example.Foo.bar(Foo.java:42)\n"
                        + "\tat com.example.App.main(App.java:10)";
        JavaStackTraceParser p = new JavaStackTraceParser();
        List<StackTraceParser.TraceFrame> frames = p.parse(trace);
        assertFalse(frames.isEmpty());
        assertEquals("Foo.java", frames.get(0).file());
        assertEquals(42, frames.get(0).line());
    }

    @Test
    void pythonParser_extractsFrames() {
        String trace =
                "Traceback (most recent call last):\n"
                        + "  File \"/work/app.py\", line 12, in <module>\n"
                        + "    main()";
        PyStackTraceParser p = new PyStackTraceParser();
        var frames = p.parse(trace);
        assertFalse(frames.isEmpty());
        assertEquals("/work/app.py", frames.get(0).file());
        assertEquals(12, frames.get(0).line());
    }

    @Test
    void nodeParser_extractsFrames() {
        String trace = "TypeError: x is not a function\n    at doThing (/app/index.js:15:5)";
        NodeStackTraceParser p = new NodeStackTraceParser();
        var frames = p.parse(trace);
        assertFalse(frames.isEmpty());
        assertEquals("/app/index.js", frames.get(0).file());
        assertEquals(15, frames.get(0).line());
    }
}
