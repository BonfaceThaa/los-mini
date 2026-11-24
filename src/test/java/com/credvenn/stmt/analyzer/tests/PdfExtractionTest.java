package com.credvenn.stmt.analyzer.tests;

import com.credvenn.stmt.analyzer.core.*;
import com.credvenn.stmt.analyzer.core.extraction.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PdfExtractionTest {
    private static AnalysisInput input(byte[] bytes, String password) {
        return new AnalysisInput(bytes, password, "MPESA", "extract-test");
    }

    @Test
    void extractsOrderedPageTextAndSourceCoordinates() throws Exception {
        var result = new PdfBoxTextExtractor().extract(input(SyntheticPdfs.text(2, null, true), null));
        assertEquals("extract-test", result.correlationId());
        assertEquals(2, result.pages().size());
        assertTrue(result.warnings().isEmpty());
        for (int i = 0; i < 2; i++) {
            var page = result.pages().get(i);
            assertEquals(i + 1, page.number());
            try (var expected = getClass().getResourceAsStream("/fixtures/expected-page-1.txt")) {
                assertNotNull(expected);
                String text = new String(expected.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                assertEquals(text.replace("PAGE 1", "PAGE " + (i + 1)), page.text());
            }
            assertEquals(72, page.glyphs().getFirst().x(), 0.01);
            assertEquals(72, page.glyphs().getFirst().y(), 0.01);
            assertTrue(page.glyphs().getFirst().width() > 0);
            assertTrue(page.glyphs().getFirst().height() > 0);
            assertEquals(0, page.rotation());
            assertThrows(UnsupportedOperationException.class, () -> page.glyphs().clear());
        }
        assertThrows(UnsupportedOperationException.class, () -> result.pages().clear());
        assertFalse(result.toString().contains("SYNTHETIC"));
        assertFalse(result.pages().getFirst().toString().contains("SYNTHETIC"));
        assertFalse(result.pages().getFirst().glyphs().getFirst().toString().contains("SYNTHETIC"));
    }

    @Test
    void encryptedPasswordSupportAndSafeFailures() throws Exception {
        String password = UUID.randomUUID().toString();
        byte[] bytes = SyntheticPdfs.text(1, password, true);
        assertEquals(1, new PdfBoxTextExtractor().extract(input(bytes, password)).pages().size());
        for (String wrong : new String[]{null, "wrong-synthetic-password"}) {
            var failure = assertThrows(AnalysisProcessingException.class,
                    () -> new PdfBoxTextExtractor().extract(input(bytes, wrong)));
            assertEquals(ProcessingErrorCode.INCORRECT_PASSWORD, failure.code());
            assertEquals("extract-test", failure.correlationId());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains(password));
        }
        byte[] restricted = SyntheticPdfs.text(1, password, false);
        assertCode(ProcessingErrorCode.EXTRACTION_NOT_PERMITTED, restricted, password, ExtractionLimits.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a PDF", "%PDF-1.7\ninvalid body"})
    void rejectsNonPdfAndMalformedPdf(String text) {
        assertCode(ProcessingErrorCode.MALFORMED_PDF, text.getBytes(StandardCharsets.US_ASCII),
                null, ExtractionLimits.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsImageOnlyAndBlankDocumentsWithoutClaimingOcr(boolean image) throws Exception {
        assertCode(ProcessingErrorCode.NO_EXTRACTABLE_TEXT, SyntheticPdfs.withoutText(image),
                null, ExtractionLimits.DEFAULT);
    }

    @Test
    void mixedDocumentRetainsPageBoundariesAndWarnsAboutTextlessPage() throws Exception {
        var result = new PdfBoxTextExtractor().extract(input(SyntheticPdfs.mixed(), null));
        assertEquals(2, result.pages().size());
        assertTrue(result.pages().get(1).text().isBlank());
        assertEquals("PAGE_WITHOUT_TEXT", result.warnings().getFirst().code());
        assertEquals(2, result.warnings().getFirst().sourcePage());
    }

    @Test
    void enforcesAllConfiguredLimitsIncludingExactByteBoundary() throws Exception {
        byte[] bytes = SyntheticPdfs.text(2, null, true);
        assertEquals(2, new PdfBoxTextExtractor(new ExtractionLimits(bytes.length, 2, 1000, 1000))
                .extract(input(bytes, null)).pages().size());
        for (var limits : new ExtractionLimits[]{
                new ExtractionLimits(bytes.length - 1, 2, 1000, 1000),
                new ExtractionLimits(bytes.length, 1, 1000, 1000),
                new ExtractionLimits(bytes.length, 2, 5, 1000),
                new ExtractionLimits(bytes.length, 2, 1000, 5)}) {
            assertCode(ProcessingErrorCode.RESOURCE_LIMIT_EXCEEDED, bytes, null, limits);
        }
    }

    @Test
    void rejectsInvalidInputsAndDocumentTypes() {
        var extractor = new PdfBoxTextExtractor();
        assertEquals(ProcessingErrorCode.INVALID_INPUT, assertThrows(AnalysisProcessingException.class,
                () -> extractor.extract(null)).code());
        assertEquals(ProcessingErrorCode.INVALID_INPUT, assertThrows(AnalysisProcessingException.class,
                () -> extractor.extract(input(new byte[0], null))).code());
        assertEquals(ProcessingErrorCode.UNSUPPORTED_DOCUMENT_TYPE, assertThrows(AnalysisProcessingException.class,
                () -> extractor.extract(new AnalysisInput(new byte[]{1}, null, "OTHER", "extract-test"))).code());
        assertThrows(IllegalArgumentException.class, () -> new ExtractionLimits(0, 1, 1, 1));
    }

    private static void assertCode(ProcessingErrorCode code, byte[] bytes, String password, ExtractionLimits limits) {
        var failure = assertThrows(AnalysisProcessingException.class,
                () -> new PdfBoxTextExtractor(limits).extract(input(bytes, password)));
        assertEquals(code, failure.code());
        assertEquals("extract-test", failure.correlationId());
        assertNull(failure.getCause());
    }
}
