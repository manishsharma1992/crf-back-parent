package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Projection tests — no Spring, no database.
 *
 * <p>Rewritten for the two-state engine: the AWAITING_EXTERNAL case is gone, visible questions now
 * come from the walk's path rather than a separate list, and flags travel on every state instead
 * of only on the terminal one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormStateAssemblerTest {

    private FormStateAssembler assembler;

    @BeforeAll
    void setUp() {
        assembler = new FormStateAssembler();
    }

    // ------------------------------------------------------------------ fixtures

    private static LocalizedQuestionLabel label(String text) {
        return new LocalizedQuestionLabel(LabelDetails.of(text), LabelDetails.of(text));
    }

    private static Option option(String value) {
        return new Option(value, new LocalizedLabel(value, value));
    }

    private static Question choice(String key, String fillsFlag, String... options) {
        return new Question(key, QuestionType.SINGLE_CHOICE, true, false, true, null, List.of(), null,
                label(key), null, null,
                List.of(options).stream().map(FormStateAssemblerTest::option).toList(),
                List.of(), List.of(),
                List.of(new Branch(Condition.defaultBranch(), null, new Effect(null, Map.of(), true))),
                fillsFlag);
    }

    private static Question computed(String key) {
        return new Question(key, QuestionType.COMPUTED, false, true, false, null, List.of(), null,
                label(key), null, null, List.of(option("BUSINESS_GROUP"), option("BORROWER")),
                List.of(), List.of(), List.of(), null);
    }

    private static DecisionTreeDefinition definition(LeverageFormType form, List<Question> questions,
                                                     Map<RecommendationOutcome, Outcome> outcomes) {
        return new DecisionTreeDefinition(form, 3, DefinitionStatus.PUBLISHED, "EN", List.of("EN", "FR"),
                questions.get(0).key(),
                List.of(new Section("MAIN", 1, new LocalizedLabel("M", "M"), questions)),
                outcomes, Map.of(), Map.of(), List.of(), List.of());
    }

    private static TraversalResult pending(Question question, List<String> path,
                                           Map<String, String> computed, Map<String, String> flags) {
        return new TraversalResult(TraversalState.PENDING_INPUT, question, computed, flags, null, path);
    }

    private static TraversalResult terminal(List<String> path, RecommendationOutcome outcome,
                                            Map<String, String> flags) {
        return new TraversalResult(TraversalState.TERMINAL, null, Map.of(), flags, outcome, path);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class InProgress {

        @Test
        void pending_maps_to_in_progress_with_the_current_question_marked() {
            Question q01 = choice("Q01", null, "YES", "NO");
            Question q02 = choice("Q02", null, "YES", "NO");
            DecisionTreeDefinition def = definition(LeverageFormType.PRELIMINARY, List.of(q01, q02), Map.of());

            FormState state = assembler.assemble(def, Map.of("Q01", "NO"),
                    pending(q02, List.of("Q01", "Q02"), Map.of(), Map.of()));

            assertEquals(FormState.Status.IN_PROGRESS, state.status());
            assertEquals("Q02", state.nextQuestionKey());
            assertEquals(3, state.definitionVersion());
            assertNull(state.outcome());
            assertFalse(state.visibleQuestions().get(0).current());
            assertTrue(state.visibleQuestions().get(1).current());
        }

        @Test
        void visible_questions_carry_the_answers_already_given() {
            Question q01 = choice("Q01", null, "YES", "NO");
            Question q02 = choice("Q02", null, "YES", "NO");
            DecisionTreeDefinition def = definition(LeverageFormType.PRELIMINARY, List.of(q01, q02), Map.of());

            FormState state = assembler.assemble(def, Map.of("Q01", "NO"),
                    pending(q02, List.of("Q01", "Q02"), Map.of(), Map.of()));

            assertEquals("NO", state.visibleQuestions().get(0).answer());
            assertNull(state.visibleQuestions().get(1).answer());
        }

        /** Only the road actually taken is rendered. */
        @Test
        void a_question_off_the_path_is_not_rendered() {
            Question q01 = choice("Q01", null, "YES", "NO");
            Question q02 = choice("Q02", null, "YES", "NO");
            Question q03 = choice("Q03", null, "YES", "NO");
            DecisionTreeDefinition def =
                    definition(LeverageFormType.PRELIMINARY, List.of(q01, q02, q03), Map.of());

            FormState state = assembler.assemble(def, Map.of("Q01", "NO"),
                    pending(q02, List.of("Q01", "Q02"), Map.of(), Map.of()));

            assertEquals(List.of("Q01", "Q02"),
                    state.visibleQuestions().stream().map(QuestionView::key).toList());
        }

        /** The LBO flag is filled by the first question, long before the form ends. */
        @Test
        void flags_are_carried_mid_form() {
            Question q01 = choice("Q01", "ecbLboFlag", "YES", "NO");
            DecisionTreeDefinition def = definition(LeverageFormType.ECB, List.of(q01), Map.of());

            FormState state = assembler.assemble(def, Map.of("Q01", "YES"),
                    pending(q01, List.of("Q01"), Map.of(), Map.of("ecbLboFlag", "YES")));

            assertEquals("YES", state.flags().get("ecbLboFlag"));
        }

        @Test
        void a_computed_answer_is_shown_and_marked_derived() {
            Question q04 = computed("Q-S04");
            DecisionTreeDefinition def = definition(LeverageFormType.ECB, List.of(q04), Map.of());

            FormState state = assembler.assemble(def, Map.of(),
                    pending(null, List.of("Q-S04"), Map.of("Q-S04", "BUSINESS_GROUP"), Map.of()));

            QuestionView view = state.visibleQuestions().get(0);
            assertEquals("BUSINESS_GROUP", view.answer());
            assertTrue(view.derived(), "the UI must not post a derived value back");
        }

        @Test
        void sub_answers_are_split_out_by_their_dotted_prefix() {
            Question q01 = choice("Q-B01A", null, "YES", "NO");
            DecisionTreeDefinition def = definition(LeverageFormType.ECB, List.of(q01), Map.of());

            FormState state = assembler.assemble(def,
                    Map.of("Q-B01A.sovereign", "NO", "Q-B01A.financialSector", "YES"),
                    pending(q01, List.of("Q-B01A"), Map.of(), Map.of()));

            assertEquals(Map.of("sovereign", "NO", "financialSector", "YES"),
                    state.visibleQuestions().get(0).subAnswers());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Completed {

        private DecisionTreeDefinition preliminary() {
            return definition(LeverageFormType.PRELIMINARY, List.of(choice("Q01", null, "YES", "NO")),
                    Map.of(RecommendationOutcome.ECB, new Outcome("ECB", List.of(LeverageFormType.ECB),
                            Map.of("fedLeveragedFlag", "INR"))));
        }

        @Test
        void terminal_maps_to_completed_and_merges_catalogue_and_branch_flags() {
            FormState state = assembler.assemble(preliminary(), Map.of("Q01", "NO"),
                    terminal(List.of("Q01"), RecommendationOutcome.ECB, Map.of("branchFlag", "SET")));

            assertEquals(FormState.Status.COMPLETED, state.status());
            assertEquals("ECB", state.outcome().code());
            assertEquals("ECB", state.outcome().displayValue());
            assertEquals("INR", state.outcome().flags().get("fedLeveragedFlag"), "forced by the catalogue");
            assertEquals("SET", state.outcome().flags().get("branchFlag"), "set by the branch");
            assertTrue(state.outcome().formsToShow().contains(LeverageFormType.ECB));
            assertNull(state.nextQuestionKey());
        }

        /** A branch naming a flag explicitly wins over the outcome's default for it. */
        @Test
        void a_branch_flag_overrides_the_catalogues_forced_value() {
            FormState state = assembler.assemble(preliminary(), Map.of("Q01", "NO"),
                    terminal(List.of("Q01"), RecommendationOutcome.ECB,
                            Map.of("fedLeveragedFlag", "FED_NOT_LEVERAGED")));

            assertEquals("FED_NOT_LEVERAGED", state.outcome().flags().get("fedLeveragedFlag"));
        }

        /** ECB and FED express their result as flags, so they finish with no outcome at all. */
        @Test
        void an_ecb_form_completes_without_an_outcome() {
            DecisionTreeDefinition ecb =
                    definition(LeverageFormType.ECB, List.of(choice("Q01", null, "YES", "NO")), Map.of());

            FormState state = assembler.assemble(ecb, Map.of("Q01", "YES"),
                    terminal(List.of("Q01"), null, Map.of("ecbLeveragedFlag", "INR")));

            assertEquals(FormState.Status.COMPLETED, state.status());
            assertNull(state.outcome());
            assertEquals("INR", state.flags().get("ecbLeveragedFlag"));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Stranded {

        /** Nothing for an analyst to do about it, so it is not a screen. */
        @Test
        void a_stranded_walk_raises_rather_than_rendering() {
            DecisionTreeDefinition def =
                    definition(LeverageFormType.ECB, List.of(choice("Q01", null, "YES")), Map.of());
            TraversalResult stranded =
                    new TraversalResult(TraversalState.STRANDED, null, Map.of(), Map.of(), null, List.of("Q01"));

            StrandedTraversalException thrown = assertThrows(StrandedTraversalException.class,
                    () -> assembler.assemble(def, Map.of(), stranded));
            assertEquals(LeverageFormType.ECB, thrown.formType());
            assertEquals(3, thrown.version());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AnswerAdapter {

        private DecisionTreeDefinition withChecklistAndTable() {
            Question checklist = new Question("Q-B01A", QuestionType.CHECKLIST, true, false, true, null,
                    List.of(), null, label("Q-B01A"), null, null, List.of(),
                    List.of(new ChecklistItem("sovereign", new LocalizedLabel("s", "s")),
                            new ChecklistItem("sme", new LocalizedLabel("m", "m"))),
                    List.of(), List.of(), null);
            Question table = new Question("Q-F01", QuestionType.DATA_ENTRY, true, false, true, null,
                    List.of(), null, label("Q-F01"), null, null, List.of(), List.of(),
                    List.of(new DataField("ebitda", "G", new LocalizedLabel("e", "e"), null,
                            DataFieldType.NUMERIC, true, true, null, null, null)),
                    List.of(), null);
            return definition(LeverageFormType.ECB, List.of(checklist, table), Map.of());
        }

        /** The UI's flat dotted map is unchanged; only the reading of it moved. */
        @Test
        void dotted_keys_become_typed_item_answers() {
            FormAnswers answers = FormAnswers.of(withChecklistAndTable(),
                    Map.of("Q-B01A.sovereign", "NO", "Q-B01A.sme", "YES"));

            assertEquals(ItemAnswer.YES, answers.itemAnswers("Q-B01A").get("sme"));
            assertEquals(ItemAnswer.NO, answers.itemAnswers("Q-B01A").get("sovereign"));
        }

        @Test
        void a_data_entry_box_is_found_by_its_field_key_alone() {
            FormAnswers answers = FormAnswers.of(withChecklistAndTable(), Map.of("Q-F01.ebitda", "802468656"));
            assertEquals(new BigDecimal("802468656"), answers.fieldValue("ebitda").orElseThrow());
        }

        /** A cleared input arrives as "" and must read as unanswered, not as an empty answer. */
        @Test
        void blank_values_read_as_absent() {
            FormAnswers answers = FormAnswers.of(withChecklistAndTable(),
                    Map.of("Q-B01A", "  ", "Q-F01.ebitda", ""));
            assertTrue(answers.answerOf("Q-B01A").isEmpty());
            assertTrue(answers.fieldValue("ebitda").isEmpty());
            assertTrue(answers.itemAnswers("Q-B01A").isEmpty());
        }

        @Test
        void an_unknown_field_or_question_is_absent_rather_than_guessed() {
            FormAnswers answers = FormAnswers.of(withChecklistAndTable(), Map.of("Q-F01.ebitda", "10"));
            assertTrue(answers.fieldValue("nonsense").isEmpty());
            assertTrue(answers.answerOf("Q-NOPE").isEmpty());
            assertTrue(answers.itemAnswers("Q-F01").isEmpty(), "a DATA_ENTRY has no checklist items");
        }

        @Test
        void cross_form_answers_are_addressed_as_authored() {
            FormAnswers answers = FormAnswers.of(withChecklistAndTable(), Map.of(),
                    Map.of("FED/Q01", "YES"));
            assertEquals("YES", answers.crossFormAnswer("FED/Q01").orElseThrow());
            assertTrue(answers.crossFormAnswer("FED/Q99").isEmpty());
        }

        @Test
        void the_raw_map_is_preserved_for_the_snapshot() {
            Map<String, String> posted = Map.of("Q-B01A.sovereign", "NO");
            assertEquals(posted, FormAnswers.of(withChecklistAndTable(), posted).raw());
        }
    }
}
