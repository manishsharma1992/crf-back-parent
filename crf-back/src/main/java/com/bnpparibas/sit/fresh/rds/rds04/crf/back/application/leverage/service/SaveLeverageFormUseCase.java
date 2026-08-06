package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAudit;

public class SaveLeverageFormUseCase {

    private final ValidationDomainService validation;// new collaborator

    // ... unchanged up to and including analyses.save(analysis) ...

        analysis.recordSection(formType, snapshot);
        analyses.save(analysis);

    // Audit read AFTER the save: lastModifiedTimestamp has to describe the write that just
    // happened, not the one before it, or the screen shows a stamp older than the data.
        return formStateAssembler.assemble(definition, settled, result,
            validation.violations(definition, settled, result),
    locale, FormAudit.of(analysis));
}
