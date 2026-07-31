package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Turns a whole authoring workbook into the three {@link DecisionTreeDefinition} aggregates that
 * get written to {@code leverage_decision_tree_definition}.
 *
 * <p><b>One workbook, one pass, three definitions.</b> The catalogues are genuinely shared — the
 * LEVERAGED_FLAG value set is written by ECB and by FED, and code 2 (INR) belongs to both — so
 * parsing per form would either duplicate them or leave each form blind to the other's codes.
 * Reading once is also what makes "publish all three or none" possible one layer up.
 *
 * <p><b>Assembly is not validation.</b> This class builds objects and records what it could not
 * build. Whether the wiring is sound — reachability, cycles, unknown flags, uncovered options —
 * belongs to {@code DecisionTreeValidator}, which needs the assembled aggregate to answer any of
 * it. The two run in sequence and their outputs are joined for the BA in the report.
 *
 * <p><b>Version and status come from the caller.</b> The assembler never touches a database: the
 * orchestrating service reads {@code MAX(version)} per form and hands the answer in. That keeps
 * this class a pure function of the workbook, which is what makes it testable in memory.
 */
@DomainDrivenDesign.ApplicationService
public final class DecisionTreeAssembler {

    private final FormsSheetParser formsParser;
    private final FieldsSheetParser fieldsParser;
    private final QuestionSheetParser questionParser;

    public DecisionTreeAssembler(FormsSheetParser formsParser,
                                 FieldsSheetParser fieldsParser,
                                 QuestionSheetParser questionParser) {
        this.formsParser = formsParser;
        this.fieldsParser = fieldsParser;
        this.questionParser = questionParser;
    }

    /**
     * Decides the version each form is being published as. Implemented over the repository by the
     * orchestrating service; a fixed value in tests.
     */
    @FunctionalInterface
    public interface VersionPolicy {
        int versionFor(LeverageFormType form);
    }

    public AssembledWorkbook assemble(WorkbookSource workbook,
                                      DefinitionStatus status,
                                      VersionPolicy versions,
                                      ImportIssues issues) {

        SourceIndex index = SourceIndex.recording();
        ParsedCatalogues catalogues = formsParser.parse(workbook, issues, index);
        Map<LeverageFormType, Map<String, List<DataField>>> fields =
                fieldsParser.parse(workbook, issues, index);

        Map<LeverageFormType, DecisionTreeDefinition> definitions = new EnumMap<>(LeverageFormType.class);
        for (LeverageFormType form : LeverageFormType.values()) {
            DecisionTreeDefinition definition =
                    assembleOne(workbook, form, catalogues, fields, status, versions, issues, index);
            if (definition != null) definitions.put(form, definition);
        }
        return new AssembledWorkbook(Collections.unmodifiableMap(definitions), catalogues, index);
    }

    private DecisionTreeDefinition assembleOne(WorkbookSource workbook,
                                               LeverageFormType form,
                                               ParsedCatalogues catalogues,
                                               Map<LeverageFormType, Map<String, List<DataField>>> fields,
                                               DefinitionStatus status,
                                               VersionPolicy versions,
                                               ImportIssues issues,
                                               SourceIndex index) {

        FormMetadata metadata = catalogues.metadata().get(form);
        if (metadata == null) {
            // FORM_MISSING was already recorded by FormsSheetParser; without an entry question
            // there is no aggregate to build, so this form is skipped rather than half-built.
            return null;
        }
        List<Question> questions = questionParser.parse(
                workbook, form, fields.getOrDefault(form, Map.of()), issues, index);
        if (questions.isEmpty()) {
            issues.add(SourceLocation.of(QuestionSheetParser.sheetNameFor(form), 0),
                    "FORM_NO_QUESTIONS", "No questions were read for " + form);
        }

        return new DecisionTreeDefinition(
                form,
                versions.versionFor(form),
                status,
                metadata.defaultLocale(),
                metadata.locales(),
                metadata.entryQuestion(),
                List.of(QuestionSheetParser.singleSection(form, questions)),
                outcomesFor(form, catalogues),
                catalogues.flagsFor(form),
                catalogues.flagValueSets(),
                catalogues.messagesFor(form),
                catalogues.panelsFor(form));
    }

    /**
     * Only the preliminary form produces a recommendation; ECB and FED express their result as
     * flags. Giving them an empty catalogue is deliberate rather than tidy-minded: an
     * {@code outcome=} clause mistakenly authored on an ECB branch then fails validation with
     * OUTCOME_NOT_DECLARED instead of importing quietly and doing nothing at runtime.
     */
    private Map<RecommendationOutcome, Outcome> outcomesFor(LeverageFormType form, ParsedCatalogues catalogues) {
        return form == LeverageFormType.PRELIMINARY ? catalogues.outcomes() : Map.of();
    }
}
