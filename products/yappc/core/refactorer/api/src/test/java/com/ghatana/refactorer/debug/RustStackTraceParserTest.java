package com.ghatana.refactorer.debug;

/** Tests for {@link RustStackTraceParser}. 
 * @doc.type class
 * @doc.purpose Handles rust stack trace parser test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class RustStackTraceParserTest extends BaseParserTest<RustStackTraceParser> {

    private static final String SAMPLE_TRACE =
            "thread 'main' panicked at 'index out of bounds: the len is 3 but the index is 5',"
                + " src/main.rs:10:5\n"
                + "note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace\n"
                + "stack backtrace:\n"
                + "   0: rust_begin_unwind\n"
                + "             at"
                + " /rustc/9eb3afe9e9d83b2b5b3caa6d9c6f5e22b6f1d8a7/library/std/src/panicking.rs:647:5\n"
                + "   1: core::panicking::panic_fmt\n"
                + "             at"
                + " /rustc/9eb3afe9e9d83b2b5b3caa6d9c6f5e22b6f1d8a7/library/core/src/panicking.rs:72:14\n"
                + "   2: core::panicking::panic_bounds_check\n"
                + "             at"
                + " /rustc/9eb3afe9e9d83b2b5b3caa6d9c6f5e22b6f1d8a7/library/core/src/panicking.rs:152:5\n"
                + "   3: <usize as core::slice::index::SliceIndex<[T]>>::index\n"
                + "             at"
                + " /rustc/9eb3afe9e9d83b2b5b3caa6d9c6f5e22b6f1d8a7/library/core/src/slice/index.rs:255:10\n"
                + "   4: core::slice::index::<impl core::ops::index::Index<I> for [T]>::index\n"
                + "             at"
                + " /rustc/9eb3afe9e9d83b2b5b3caa6d9c6f5e22b6f1d8a7/library/core/src/slice/index.rs:18:9\n"
                + "   5: <alloc::vec::Vec<T,A> as core::ops::index::Index<I>>::index\n"
                + "             at"
                + " /rustc/9eb3afe9e9d83b2b5b3caa6d9c6f5e22b6f1d8a7/library/alloc/src/vec/mod.rs:2796:9\n"
                + "   6: my_crate::main\n"
                + "             at ./src/main.rs:10:5";

    @Override
    protected RustStackTraceParser createParser() {
        return new RustStackTraceParser();
    }

    @Override
    protected String getExpectedFileName() {
        return "./src/main.rs";
    }

    @Override
    protected int getExpectedLineNumber() {
        return 10;
    }

    @Override
    protected String getSampleStackTrace() {
        return SAMPLE_TRACE;
    }

    // Add Rust-specific test cases here
}
