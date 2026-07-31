package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * A row of the Validation Messages table.
 *
 * @param questionKey null for a form-wide message such as the checklist one
 * @param fieldKey    null when the rule applies to the whole question
 */
@DomainDrivenDesign.ValueObject
public record ValidationMessage(String questionKey,
                                String fieldKey,
                                ValidationRule rule,
                                String messageKey,
                                Severity severity,
                                LocalizedLabel text) {
}
