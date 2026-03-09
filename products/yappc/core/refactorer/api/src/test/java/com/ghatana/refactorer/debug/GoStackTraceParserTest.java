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
                    + "main.main()\n\t/Users/example/go/src/example.com/app/main.go:10 +0x1b5";

    @Override
    protected GoStackTraceParser createParser() {
        return new GoStackTraceParser();
    }

    @Override
    protected String getExpectedFileName() {
        return "/Users/example/go/src/example.com/app/main.go";
    }

    @Override
    protected int getExpectedLineNumber() {
        return 10;
    }

    @Override
    protected String getSampleStackTrace() {
        return SAMPLE_TRACE;
    }

    // Add Go-specific test cases here
}
