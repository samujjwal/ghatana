package com.ghatana.tts.ssml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SsmlProcessor} — AV-008.3.
 *
 * @doc.type class
 * @doc.purpose SSML compliance tests for the TTS SSML processor
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SsmlProcessor [GH-90000]")
class SsmlProcessorTest {

    private final SsmlProcessor processor = SsmlProcessor.create(); // GH-90000

    // ─── parse: validation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("parse() - validation [GH-90000]")
    class ParseValidation {

        @Test
        @DisplayName("null ssml throws NullPointerException [GH-90000]")
        void parse_null_throwsNPE() { // GH-90000
            assertThatNullPointerException().isThrownBy(() -> processor.parse(null)); // GH-90000
        }

        @Test
        @DisplayName("blank ssml throws IllegalArgumentException [GH-90000]")
        void parse_blank_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException().isThrownBy(() -> processor.parse("    [GH-90000]"));
        }

        @Test
        @DisplayName("missing <speak> root throws IllegalArgumentException [GH-90000]")
        void parse_noSpeakRoot_throwsIAE() { // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> processor.parse("<say>Hello</say> [GH-90000]"))
                    .withMessageContaining("<speak> [GH-90000]");
        }
    }

    // ─── parse: plain text ────────────────────────────────────────────────────

    @Nested
    @DisplayName("parse() - plain text [GH-90000]")
    class ParsePlainText {

        @Test
        @DisplayName("plain text inside <speak> produces a single TEXT segment [GH-90000]")
        void parse_plainText_singleTextSegment() { // GH-90000
            List<SsmlProcessor.SsmlSegment> segments =
                    processor.parse("<speak>Hello world</speak> [GH-90000]");
            assertThat(segments).hasSize(1); // GH-90000
            assertThat(segments.get(0).type()) // GH-90000
                    .isEqualTo(SsmlProcessor.SsmlSegment.SegmentType.TEXT); // GH-90000
            assertThat(segments.get(0).text()).isEqualTo("Hello world [GH-90000]");
        }

        @Test
        @DisplayName("empty speak element produces no segments [GH-90000]")
        void parse_emptySpeak_noSegments() { // GH-90000
            List<SsmlProcessor.SsmlSegment> segments =
                    processor.parse("<speak>   </speak> [GH-90000]");
            assertThat(segments).isEmpty(); // GH-90000
        }
    }

    // ─── parse: break tag ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("parse() - <break> [GH-90000]")
    class ParseBreak {

        @Test
        @DisplayName("break tag in ms unit produces PAUSE segment with correct duration [GH-90000]")
        void parse_breakMs_pauseSegment() { // GH-90000
            String ssml = "<speak>Hello<break time=\"500ms\"/>World</speak>";
            List<SsmlProcessor.SsmlSegment> segments = processor.parse(ssml); // GH-90000

            long pauseCount = segments.stream() // GH-90000
                    .filter(s -> s.type() == SsmlProcessor.SsmlSegment.SegmentType.PAUSE) // GH-90000
                    .count(); // GH-90000
            assertThat(pauseCount).isEqualTo(1); // GH-90000

            SsmlProcessor.SsmlSegment.PauseSegment pause = segments.stream() // GH-90000
                    .filter(s -> s instanceof SsmlProcessor.SsmlSegment.PauseSegment) // GH-90000
                    .map(s -> (SsmlProcessor.SsmlSegment.PauseSegment) s) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(pause.durationMs()).isEqualTo(500L); // GH-90000
        }

        @Test
        @DisplayName("break tag in seconds unit converts to milliseconds [GH-90000]")
        void parse_breakSeconds_convertedToMs() { // GH-90000
            String ssml = "<speak>Hello<break time=\"2s\"/>World</speak>";
            List<SsmlProcessor.SsmlSegment> segments = processor.parse(ssml); // GH-90000

            SsmlProcessor.SsmlSegment.PauseSegment pause = segments.stream() // GH-90000
                    .filter(s -> s instanceof SsmlProcessor.SsmlSegment.PauseSegment) // GH-90000
                    .map(s -> (SsmlProcessor.SsmlSegment.PauseSegment) s) // GH-90000
                    .findFirst() // GH-90000
                    .orElseThrow(); // GH-90000
            assertThat(pause.durationMs()).isEqualTo(2_000L); // GH-90000
        }

        @Test
        @DisplayName("text before and after break are captured as TEXT segments [GH-90000]")
        void parse_breakWithTextAround_threeSegments() { // GH-90000
            String ssml = "<speak>Hello<break time=\"300ms\"/>World</speak>";
            List<SsmlProcessor.SsmlSegment> segments = processor.parse(ssml); // GH-90000

            assertThat(segments).hasSize(3); // GH-90000
            assertThat(segments.get(0).text()).isEqualTo("Hello [GH-90000]");
            assertThat(segments.get(1).type()) // GH-90000
                    .isEqualTo(SsmlProcessor.SsmlSegment.SegmentType.PAUSE); // GH-90000
            assertThat(segments.get(2).text()).isEqualTo("World [GH-90000]");
        }
    }

    // ─── parse: prosody ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("parse() - <prosody> [GH-90000]")
    class ParseProsody {

        @Test
        @DisplayName("prosody tag produces a PROSODY segment [GH-90000]")
        void parse_prosody_segment() { // GH-90000
            String ssml = "<speak><prosody rate=\"150%\" pitch=\"+10%\">Fast speech</prosody></speak>";
            List<SsmlProcessor.SsmlSegment> segments = processor.parse(ssml); // GH-90000

            assertThat(segments).hasSize(1); // GH-90000
            assertThat(segments.get(0).type()) // GH-90000
                    .isEqualTo(SsmlProcessor.SsmlSegment.SegmentType.PROSODY); // GH-90000
            assertThat(segments.get(0).text()).isEqualTo("Fast speech [GH-90000]");
        }

        @Test
        @DisplayName("prosody segment retains attributes string [GH-90000]")
        void parse_prosody_attributesRetained() { // GH-90000
            String ssml = "<speak><prosody rate=\"80%\">Slow</prosody></speak>";
            SsmlProcessor.SsmlSegment.ProsodySegment seg = (SsmlProcessor.SsmlSegment.ProsodySegment) // GH-90000
                    processor.parse(ssml).get(0); // GH-90000
            assertThat(seg.attributes()).contains("rate [GH-90000]");
        }
    }

    // ─── parse: emphasis ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("parse() - <emphasis> [GH-90000]")
    class ParseEmphasis {

        @Test
        @DisplayName("strong emphasis produces EMPHASIS segment [GH-90000]")
        void parse_strongEmphasis_segment() { // GH-90000
            String ssml = "<speak><emphasis level=\"strong\">Important!</emphasis></speak>";
            List<SsmlProcessor.SsmlSegment> segments = processor.parse(ssml); // GH-90000

            assertThat(segments).hasSize(1); // GH-90000
            assertThat(segments.get(0).type()) // GH-90000
                    .isEqualTo(SsmlProcessor.SsmlSegment.SegmentType.EMPHASIS); // GH-90000
            assertThat(segments.get(0).text()).isEqualTo("Important! [GH-90000]");
        }

        @Test
        @DisplayName("emphasis level is preserved in the segment [GH-90000]")
        void parse_emphasisLevel_preserved() { // GH-90000
            String ssml = "<speak><emphasis level=\"reduced\">Quiet</emphasis></speak>";
            SsmlProcessor.SsmlSegment.EmphasisSegment seg = (SsmlProcessor.SsmlSegment.EmphasisSegment) // GH-90000
                    processor.parse(ssml).get(0); // GH-90000
            assertThat(seg.emphasisLevel()).isEqualToIgnoringCase("reduced [GH-90000]");
        }
    }

    // ─── toPlainText ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toPlainText() [GH-90000]")
    class ToPlainText {

        @Test
        @DisplayName("strips all SSML tags leaving only text [GH-90000]")
        void toPlainText_stripsAllTags() { // GH-90000
            String ssml = "<speak>Hello <emphasis level=\"strong\">World</emphasis><break time=\"200ms\"/></speak>";
            assertThat(processor.toPlainText(ssml)).isEqualTo("Hello World [GH-90000]");
        }

        @Test
        @DisplayName("null ssml throws NullPointerException [GH-90000]")
        void toPlainText_null_throwsNPE() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> processor.toPlainText(null)); // GH-90000
        }

        @Test
        @DisplayName("plain text input passes through unchanged [GH-90000]")
        void toPlainText_plainInput() { // GH-90000
            assertThat(processor.toPlainText("Hello World [GH-90000]")).isEqualTo("Hello World [GH-90000]");
        }
    }

    // ─── parse result is unmodifiable ─────────────────────────────────────────

    @Test
    @DisplayName("parse() returns an unmodifiable list [GH-90000]")
    void parse_returnsUnmodifiableList() { // GH-90000
        List<SsmlProcessor.SsmlSegment> segments = processor.parse("<speak>test</speak> [GH-90000]");
        assertThatThrownBy(() -> segments.add(SsmlProcessor.SsmlSegment.text("extra [GH-90000]")))
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }
}
