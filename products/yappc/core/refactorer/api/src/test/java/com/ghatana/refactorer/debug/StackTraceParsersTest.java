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
    void javaParser_extractsFrames() { // GH-90000
        String trace =
                "java.lang.NullPointerException\n"
                        + "\tat com.example.Foo.bar(Foo.java:42)\n" // GH-90000
                        + "\tat com.example.App.main(App.java:10)"; // GH-90000
        JavaStackTraceParser p = new JavaStackTraceParser(); // GH-90000
        List<StackTraceParser.TraceFrame> frames = p.parse(trace); // GH-90000
        assertFalse(frames.isEmpty()); // GH-90000
        assertEquals("Foo.java", frames.get(0).file()); // GH-90000
        assertEquals(42, frames.get(0).line()); // GH-90000
    }

    @Test
    void pythonParser_extractsFrames() { // GH-90000
        String trace =
                "Traceback (most recent call last):\n" // GH-90000
                        + "  File \"/work/app.py\", line 12, in <module>\n"
                        + "    main()"; // GH-90000
        PyStackTraceParser p = new PyStackTraceParser(); // GH-90000
        var frames = p.parse(trace); // GH-90000
        assertFalse(frames.isEmpty()); // GH-90000
        assertEquals("/work/app.py", frames.get(0).file()); // GH-90000
        assertEquals(12, frames.get(0).line()); // GH-90000
    }

    @Test
    void nodeParser_extractsFrames() { // GH-90000
        String trace = "TypeError: x is not a function\n    at doThing (/app/index.js:15:5)"; // GH-90000
        NodeStackTraceParser p = new NodeStackTraceParser(); // GH-90000
        var frames = p.parse(trace); // GH-90000
        assertFalse(frames.isEmpty()); // GH-90000
        assertEquals("/app/index.js", frames.get(0).file()); // GH-90000
        assertEquals(15, frames.get(0).line()); // GH-90000
    }

    @Test
    void nodeParser_extractsFileUrlFrames() { // GH-90000
        String trace =
                "TypeError: x is not a function\n"
                        + "    at async loadConfig (file:///app/dist/index.mjs:27:13)\n" // GH-90000
                        + "    at file:///app/dist/index.mjs:42:3";
        NodeStackTraceParser p = new NodeStackTraceParser(); // GH-90000
        var frames = p.parse(trace); // GH-90000
        assertFalse(frames.isEmpty()); // GH-90000
        assertEquals("file:///app/dist/index.mjs", frames.get(0).file()); // GH-90000
        assertEquals(27, frames.get(0).line()); // GH-90000
        assertEquals("async loadConfig", frames.get(0).function()); // GH-90000
    }
}
