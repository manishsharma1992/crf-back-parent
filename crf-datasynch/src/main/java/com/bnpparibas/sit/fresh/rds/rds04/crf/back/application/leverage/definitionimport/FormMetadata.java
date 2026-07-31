package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;

import java.util.List;

/** One row of the FORM METADATA table: everything about a form that is not a question. */
public record FormMetadata(LeverageFormType formType,
                           String defaultLocale,
                           List<String> locales,
                           String entryQuestion) {
}
