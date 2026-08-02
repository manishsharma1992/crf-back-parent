package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeValidator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.infrastructure.leverage.definitionimport.excel
        .PoiWorkbookSourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the decision-tree import.
 *
 * <p>Explicit {@code @Bean} methods rather than component scanning, for the same reason the domain
 * carries no Spring annotations: what is a singleton and what is per-request should be a decision
 * someone made, not a side effect of where a class happens to live.
 *
 * <p><b>Three collaborators are deliberately absent.</b> {@code SourceIndex} and
 * {@code ImportIssues} are per-import scratchpads, and {@code ExcelSourceLocator},
 * {@code ValidationReportAssembler} and {@code PoiWorkbookSource} each hold one of them or one open
 * stream. As singletons they would be shared across concurrent imports — two BAs uploading at once
 * would get each other's row numbers in their error reports, which is worse than a startup failure
 * because it would not look like a bug. They are constructed by hand, where their lifetime is
 * obvious:
 *
 * <ul>
 *   <li>{@code SourceIndex} — by {@code DecisionTreeAssembler.assemble}</li>
 *   <li>{@code ExcelSourceLocator} — by {@code AssembledWorkbook.locator()}</li>
 *   <li>{@code ValidationReportAssembler} — by {@code DecisionTreeImportService}</li>
 *   <li>{@code PoiWorkbookSource} — by {@code PoiWorkbookSourceFactory}, closed by the controller</li>
 * </ul>
 */
@Configuration
public class DecisionTreeImportConfiguration {

    // ---------------------------------------------------------------- grammar

    @Bean
    public ConditionExpressionParser conditionExpressionParser() {
        return new ConditionExpressionParser();
    }

    @Bean
    public BranchExpressionParser branchExpressionParser(ConditionExpressionParser conditionParser) {
        return new BranchExpressionParser(conditionParser);
    }

    @Bean
    public ValueRuleExpressionParser valueRuleExpressionParser(ConditionExpressionParser conditionParser) {
        return new ValueRuleExpressionParser(conditionParser);
    }

    @Bean
    public LabelParser labelParser() {
        return new LabelParser();
    }

    @Bean
    public OptionsParser optionsParser() {
        return new OptionsParser();
    }

    // ---------------------------------------------------------------- sheets

    @Bean
    public FlagValuesSheetParser flagValuesSheetParser() {
        return new FlagValuesSheetParser();
    }

    @Bean
    public FormsSheetParser formsSheetParser(FlagValuesSheetParser flagValuesParser) {
        return new FormsSheetParser(flagValuesParser);
    }

    @Bean
    public FieldsSheetParser fieldsSheetParser() {
        return new FieldsSheetParser();
    }

    @Bean
    public QuestionSheetParser questionSheetParser(LabelParser labelParser,
                                                   OptionsParser optionsParser,
                                                   BranchExpressionParser branchParser,
                                                   ValueRuleExpressionParser valueRuleParser) {
        return new QuestionSheetParser(labelParser, optionsParser, branchParser, valueRuleParser);
    }

    @Bean
    public DecisionTreeAssembler decisionTreeAssembler(FormsSheetParser formsParser,
                                                       FieldsSheetParser fieldsParser,
                                                       QuestionSheetParser questionParser) {
        return new DecisionTreeAssembler(formsParser, fieldsParser, questionParser);
    }

    // ---------------------------------------------------------------- adapters and domain

    @Bean
    public WorkbookSourceFactory workbookSourceFactory() {
        return new PoiWorkbookSourceFactory();
    }

    /**
     * The validator is a pure domain service with no dependencies, so it is declared here rather
     * than annotated — nothing in {@code crf-shared} should know Spring exists.
     */
    @Bean
    public DecisionTreeValidator decisionTreeValidator() {
        return new DecisionTreeValidator();
    }
}
