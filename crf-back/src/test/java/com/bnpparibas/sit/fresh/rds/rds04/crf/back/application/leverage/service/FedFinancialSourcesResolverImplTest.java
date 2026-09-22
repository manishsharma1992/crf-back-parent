package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

@ExtendWith(MockitoExtension.class)
class FedFinancialSourcesResolverImplTest {

    private static final long FINANCIALS_ID = 42L;

    @Mock
    private FedFinancialsDao dao;

    @InjectMocks
    private FedFinancialSourcesResolverImpl resolver;

    @Test
    @DisplayName("maps both columns onto the tokens the Fields tab names")
    void mapsBothColumns() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1000", "300")));

        FedFinancialSources sources = resolver.resolve(subject(FINANCIALS_ID));

        // These two strings are the contract with the workbook: aplcTotalBSDebt and
        // aplcBookValueOfEquity say "Derived From = FINANCIALS/gross_debt" and
        // "FINANCIALS/total_equity". A mismatch fails silently at runtime, so it fails loudly here.
        assertThat(sources.of("FINANCIALS/gross_debt")).contains(new BigDecimal("1000"));
        assertThat(sources.of("FINANCIALS/total_equity")).contains(new BigDecimal("300"));
    }

    @Test
    @DisplayName("a null column stays absent")
    void nullColumnAbsent() {
        when(dao.findFedFiguresById(FINANCIALS_ID)).thenReturn(Optional.of(figures("1000", null)));

        FedFinancialSources sources = resolver.resolve(subject(FINANCIALS_ID));

        assertThat(sources.of("FINANCIALS/gross_debt")).isPresent();
        assertThat(sources.of("FINANCIALS/total_equity")).isEmpty();
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

    private static FedFinancialsDao.FedFinancialFigures figures(String grossDebt, String totalEquity) {
        return new FedFinancialsDao.FedFinancialFigures() {
            @Override
            public BigDecimal getGrossDebt() {
                return grossDebt == null ? null : new BigDecimal(grossDebt);
            }

            @Override
            public BigDecimal getTotalEquity() {
                return totalEquity == null ? null : new BigDecimal(totalEquity);
            }
        };
    }
}

