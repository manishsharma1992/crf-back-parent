package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The three definitions a workbook produced, plus the catalogues they were built from.
 *
 * <p>A form is ABSENT when its metadata row or its question tab could not be read; the
 * accompanying {@link ImportIssues} says why. Callers must decide what an incomplete workbook
 * means — the import service refuses to publish anything unless all three are present, because
 * the forms reference each other's flag codes and a half-published set is not a coherent state.
 *
 * <p>The catalogues are kept alongside so the report and the persistence layer can describe what
 * was imported without re-reading the workbook.
 */
public record AssembledWorkbook(Map<LeverageFormType, DecisionTreeDefinition> definitions,
                                ParsedCatalogues catalogues,
                                SourceIndex sourceIndex) {

    /**
     * The locator that turns this workbook's validation errors into cell references. Built here
     * because only an assembled workbook has both the definitions and the rows they came from.
     */
    public SourceLocator locator() {
        return new ExcelSourceLocator(sourceIndex);
    }

    public Optional<DecisionTreeDefinition> definition(LeverageFormType form) {
        return Optional.ofNullable(definitions.get(form));
    }

    public boolean isComplete() {
        return definitions.keySet().containsAll(List.of(LeverageFormType.values()));
    }

    public List<LeverageFormType> missingForms() {
        return List.of(LeverageFormType.values()).stream()
                .filter(form -> !definitions.containsKey(form))
                .toList();
    }
}
