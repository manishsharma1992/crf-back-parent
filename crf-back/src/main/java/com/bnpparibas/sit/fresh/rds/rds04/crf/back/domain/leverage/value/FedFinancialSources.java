package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

/**
 * The FINSTAR figures the FED financial tables prefill from, keyed by the column token the Fields
 * tab names in {@code Derived From} — {@code FINANCIALS/gross_debt} is looked up as
 * {@code gross_debt}.
 *
 * <p><b>Deliberately not {@code FinancialInputs.Sources}.</b> That record is ECB's, and it encodes
 * an ECB rule: its three sources are read-only, so FINSTAR's value always wins. FED's sources are
 * prefilled AND editable — the analyst may overwrite them — so the precedence is the other way
 * round. One record serving both would be forced to get one of them wrong.
 *
 * <p><b>Keyed by token rather than by named component</b>, so adding a source is a query change
 * and a row on the Fields tab, not a new record field threaded through three layers. General
 * Obligor / Utilities in v15 will prefill Accounting EBITDA from FINSTAR, and it should cost one
 * column in the query.
 *
 * <p><b>Absent stays absent.</b> A null column is simply not in the map. It is never a zero —
 * {@code SOURCE_EMPTY} and {@code MUST_NOT_BE_ZERO} are different rules with different messages,
 * and this type must not collapse them.
 */
@DomainDrivenDesign.ValueObject
public record FedFinancialSources(Map<String, BigDecimal> byColumn) {

    /** The prefix the Fields tab uses for a FINSTAR source. */
    public static final String PREFIX = "FINANCIALS/";

    /** No FINSTAR row, or no analysed financials at all. */
    public static final FedFinancialSources NONE = new FedFinancialSources(Map.of());

    public FedFinancialSources {
        Map<String, BigDecimal> kept = new LinkedHashMap<>();
        if (byColumn != null) {
            byColumn.forEach((column, value) -> {
                if (column != null && value != null) {
                    kept.put(column, value);
                }
            });
        }
        byColumn = Collections.unmodifiableMap(kept);
    }

    /**
     * The figure a field's {@code Derived From} points at.
     *
     * @param derivedFrom as authored, e.g. {@code FINANCIALS/total_equity}
     * @return empty for a null or non-FINANCIALS source ({@code CALC/x}, analyst-typed), for a
     *         column this release does not read, and for a column FINSTAR holds no value in
     */
    public Optional<BigDecimal> of(String derivedFrom) {
        if (derivedFrom == null || !derivedFrom.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byColumn.get(derivedFrom.substring(PREFIX.length())));
    }
}

