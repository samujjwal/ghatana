package com.ghatana.refactorer.debug;

/** Tests for {@link GoStackTraceParser}.
 * @doc.type class
 * @doc.purpose Handles go stack trace parser test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class GoStackTraceParserTest extends BaseParserTest<GoStackTraceParser> {

    private static final String SAMPLE_TRACE =
            "panic: runtime error: index out of range [5] with length 3\n\n"
                    + "goroutine 1 [running]:\n"
                    + "main.main()\n\t/Users/example/go/src/example.com/app/main.go:10 +0x1b5"; // GH-90000

    @Override
    protected GoStackTraceParser createParser() { // GH-90000
        return new GoStackTraceParser(); // GH-90000
    }

    @Override
    protected String getExpectedFileName() { // GH-90000
        return "/Users/example/go/src/example.com/app/main.go";
    }

    @Override
    protected int getExpectedLineNumber() { // GH-90000
        return 10;
    }

    @Override
    protected String getSampleStackTrace() { // GH-90000
        return SAMPLE_TRACE;
    }

    // Add Go-specific test cases here
}
