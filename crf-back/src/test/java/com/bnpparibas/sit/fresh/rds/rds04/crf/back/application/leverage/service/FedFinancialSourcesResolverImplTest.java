package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

@ExtendWith(MockitoExtension.class)
class FedFinancialSourcesResolverImplTest {

    private static final long FINANCIALS_ID = 42L;

    private static final String GROSS_DEBT = "FINANCIALS/gross_debt";
    private static final String TOTAL_EQUITY = "FINANCIALS/total_equity";
    private static final String EBITDA = "FINANCIALS/ebitda";
    private static final String CASH = "FINANCIALS/cash_marketable_securities";

    @Mock
    private FedFinancialsDao dao;

    @InjectMocks
    private FedFinancialSourcesResolverImpl resolver;

    @Test
    @DisplayName("maps every column onto the token the Fields tab names")
    void mapsEveryColumn() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1000", "300", "200", "100")));

        FedFinancialSources sources = resolver.resolve(subject(FINANCIALS_ID));

        // These strings are the contract with the workbook's Derived From column:
        //   aplcTotalBSDebt, goRaTotalBSDebt        -> FINANCIALS/gross_debt
        //   aplcBookValueOfEquity                   -> FINANCIALS/total_equity
        //   goCeAccountingEbitda, goRaAccountingEbitda -> FINANCIALS/ebitda
        //   goRaCash                                -> FINANCIALS/cash_marketable_securities
        // A mismatch fails silently at runtime (the box just stays empty), so it fails loudly here.
        assertThat(sources.of(GROSS_DEBT)).contains(new BigDecimal("1000"));
        assertThat(sources.of(TOTAL_EQUITY)).contains(new BigDecimal("300"));
        assertThat(sources.of(EBITDA)).contains(new BigDecimal("200"));
        assertThat(sources.of(CASH)).contains(new BigDecimal("100"));
    }

    @Test
    @DisplayName("each column comes from its own getter - no two tokens share a value")
    void columnsAreNotCrossed() {
        // Distinct values, so a copy-paste slip (cash mapped from getEbitda, say) cannot pass.
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1", "2", "3", "4")));

        FedFinancialSources sources = resolver.resolve(subject(FINANCIALS_ID));

        assertThat(sources.of(GROSS_DEBT)).contains(new BigDecimal("1"));
        assertThat(sources.of(TOTAL_EQUITY)).contains(new BigDecimal("2"));
        assertThat(sources.of(EBITDA)).contains(new BigDecimal("3"));
        assertThat(sources.of(CASH)).contains(new BigDecimal("4"));
    }

    @Test
    @DisplayName("a null column stays absent, never zero - the analyst types it instead")
    void nullColumnAbsent() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1000", null, null, null)));

        FedFinancialSources sources = resolver.resolve(subject(FINANCIALS_ID));

        assertThat(sources.of(GROSS_DEBT)).isPresent();
        assertThat(sources.of(TOTAL_EQUITY)).isEmpty();
        assertThat(sources.of(EBITDA)).isEmpty();
        assertThat(sources.of(CASH)).isEmpty();
    }

    @Test
    @DisplayName("a zero column is a real zero, not absent")
    void zeroColumnPresent() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1000", "300", "200", "0")));

        assertThat(resolver.resolve(subject(FINANCIALS_ID)).of(CASH))
                .hasValueSatisfying(cash -> assertThat(cash).isEqualByComparingTo("0"));
    }

    @Test
    @DisplayName("an unknown token is absent")
    void unknownToken() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1000", "300", "200", "100")));

        assertThat(resolver.resolve(subject(FINANCIALS_ID)).of("FINANCIALS/net_debt")).isEmpty();
    }

    @Test
    @DisplayName("COLUMNS lists exactly the columns served - the walk test checks the workbook against it")
    void columnsAreTheServedSet() {
        assertThat(FedFinancialSourcesResolverImpl.COLUMNS)
                .containsExactlyInAnyOrder("gross_debt", "total_equity", "ebitda", "cash_marketable_securities");
    }

    @Test
    @DisplayName("no FINSTAR row resolves to NONE")
    void noRow() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(subject(FINANCIALS_ID))).isEqualTo(FedFinancialSources.NONE);
    }

    @Test
    @DisplayName("no subject, or no financials id, never touches the database")
    void noSubject() {
        assertThat(resolver.resolve(null)).isEqualTo(FedFinancialSources.NONE);
        assertThat(resolver.resolve(subject(null))).isEqualTo(FedFinancialSources.NONE);
        verify(dao, never()).findFedFiguresById(anyLong());
    }

    // ------------------------------------------------------------------ helpers

    private static AnalysisSubject subject(Long financialsId) {
        AnalysisSubject subject = mock(AnalysisSubject.class);
        when(subject.financialsId()).thenReturn(financialsId);
        return subject;
    }

    private static FedFinancialsDao.FedFinancialFigures figures(String grossDebt, String totalEquity,
                                                                String ebitda, String cash) {
        return new FedFinancialsDao.FedFinancialFigures() {
            @Override
            public BigDecimal getGrossDebt() {
                return amount(grossDebt);
            }

            @Override
            public BigDecimal getTotalEquity() {
                return amount(totalEquity);
            }

            @Override
            public BigDecimal getEbitda() {
                return amount(ebitda);
            }

            @Override
            public BigDecimal getCashMarketableSecurities() {
                return amount(cash);
            }
        };
    }

    private static BigDecimal amount(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
