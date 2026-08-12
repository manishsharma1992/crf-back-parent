package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FinancialInputs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the three prefilled figures of the financial table off the analysed FINSTAR row.
 *
 * <p>Same shape as {@code DerivedValueResolverImpl}, {@code InfoPanelResolverImpl} and
 * {@code EntityEligibilityResolverImpl}: the port is declared in the application layer, the
 * adapter lives here, and Spring supplies it. The application layer names the interface and never
 * the DAO.
 *
 * <p><b>Deliberately not a case inside {@code DerivedValueResolverImpl}.</b> That resolver answers
 * display STRINGS for question-level {@code Derived From}, and formatting a figure only to parse
 * it back for the arithmetic would be a round trip through text with a rounding hazard at each
 * end. These three stay {@link java.math.BigDecimal} from the column to the calculation.
 *
 * <p><b>Absent stays absent.</b> No row, or a null column, yields a null component. Nothing here
 * substitutes zero — {@code SOURCE_EMPTY} and {@code MUST_NOT_BE_ZERO} are different rules with
 * different messages, and this adapter must not collapse them.
 */
@Component
@RequiredArgsConstructor
public class FinancialsResolverImpl implements FinancialsResolver {

    private static final FinancialInputs.Sources NOTHING =
            new FinancialInputs.Sources(null, null, null);

    private final FinancialsDerivationDao financialsDao;

    @Override
    public FinancialInputs.Sources resolve(AnalysisSubject subject) {
        if (subject == null || subject.financialsId() == null) {
            return NOTHING;
        }
        return financialsDao.findFiguresById(subject.financialsId())
                .map(figures -> new FinancialInputs.Sources(
                        figures.getEbitda(),
                        figures.getGrossDebt(),
                        figures.getNetDebt()))
                .orElse(NOTHING);
    }
}
