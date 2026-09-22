package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

class FedFinancialSourcesTest {

    private static FedFinancialSources sources(String column, String value) {
        Map<String, BigDecimal> map = new HashMap<>();
        map.put(column, value == null ? null : new BigDecimal(value));
        return new FedFinancialSources(map);
    }

    @Test
    @DisplayName("resolves a FINANCIALS/ source by the column token after the prefix")
    void resolvesByToken() {
        assertThat(sources("total_equity", "300").of("FINANCIALS/total_equity"))
                .contains(new BigDecimal("300"));
    }

    @Test
    @DisplayName("a null column is absent, never zero")
    void nullColumnIsAbsent() {
        // SOURCE_EMPTY and MUST_NOT_BE_ZERO are different rules with different messages;
        // reading a missing FINSTAR figure as zero would fire the wrong one.
        FedFinancialSources sources = sources("total_equity", null);

        assertThat(sources.of("FINANCIALS/total_equity")).isEmpty();
        assertThat(sources.byColumn()).doesNotContainKey("total_equity");
    }

    @Test
    @DisplayName("a genuine zero is kept as zero")
    void zeroIsKept() {
        assertThat(sources("total_equity", "0").of("FINANCIALS/total_equity"))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CALC/aplcAdjustedBookValueOfEquity", "total_equity", "financials/total_equity"})
    @DisplayName("anything that is not a FINANCIALS/ source resolves to nothing")
    void nonFinancialsSources(String derivedFrom) {
        // Includes the lower-case prefix on purpose: the Fields tab is matched exactly, and a
        // case-insensitive match here would hide a typo in the workbook instead of surfacing it.
        assertThat(sources("total_equity", "300").of(derivedFrom)).isEmpty();
    }

    @Test
    @DisplayName("a column this release does not read resolves to nothing")
    void unknownColumn() {
        assertThat(sources("total_equity", "300").of("FINANCIALS/ebitda")).isEmpty();
    }

    @Test
    @DisplayName("NONE resolves nothing")
    void none() {
        assertThat(FedFinancialSources.NONE.of("FINANCIALS/gross_debt")).isEmpty();
    }

    @Test
    @DisplayName("tolerates a null map and is immutable afterwards")
    void immutable() {
        assertThat(new FedFinancialSources(null).byColumn()).isEmpty();
        FedFinancialSources sources = sources("gross_debt", "1000");
        assertThatThrownBy(() -> sources.byColumn().put("x", BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

