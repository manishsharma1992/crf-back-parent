package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import java.time.LocalDate;

/**
 * The three ratings a counterparty carries, each with the date it was set.
 *
 * <p><b>One row, one read.</b> The REIT path asks for all three on the same analysis, and they
 * come from the same {@code counterparty_characteristics} row. Fetching them one at a time would
 * be three round trips per traversal — and a traversal runs on every answer — with the worse
 * problem that three reads can disagree if the row is written between them. The same argument
 * {@code FinancialsDerivationDao} makes for the financial figures.
 *
 * <p><b>Every component is nullable and null means ABSENT.</b> A counterparty with no S&amp;P
 * rating is ordinary, not an error; a rating with no date is odd but not ours to refuse.
 */
@DomainDrivenDesign.ValueObject
public record CounterpartyRatings(String bnppRating,
                                  LocalDate bnppRatingDate,
                                  String spIssuerRating,
                                  LocalDate spIssuerRatingDate,
                                  String moodysIssuerRating,
                                  LocalDate moodysIssuerRatingDate) {

    public static final CounterpartyRatings NONE =
            new CounterpartyRatings(null, null, null, null, null, null);
}
