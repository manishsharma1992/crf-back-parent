package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Row of {@code leverage_decision_tree_definition}.
 *
 * <p><b>No ObjectMapper anywhere.</b> The {@code definition} field is typed as the DOMAIN
 * aggregate and annotated {@code @JdbcTypeCode(SqlTypes.JSON)}; Hibernate does the conversion to
 * and from {@code jsonb} itself. No hand-written serialiser, no DTO mirror to keep in step, and —
 * critically — no place for a mapping bug to silently reshape a published tree.
 *
 * <p>The domain records stay clean: the annotation lives HERE, on the entity field, never on
 * {@link DecisionTreeDefinition} or anything it holds. The domain does not know it is persisted.
 *
 * <p><b>What this costs.</b> The stored JSON mirrors the domain record graph, so a published
 * definition is only readable while those records can still absorb it. Adding a component is safe
 * (older JSON simply omits it and it reads as null); RENAMING or REMOVING one is not, and would
 * strand every stored version. Treat the record components as a published schema — additive
 * changes only. If that ever becomes too tight, the escape is a persistence mirror in this
 * package, not a mapper wired into the domain.
 *
 * <p>Audit columns ({@code created_by}, {@code created_timestamp}, {@code modified_by},
 * {@code modified_timestamp}) come from {@code BaseEntity}, so nothing here passes an author.
 *
 * <p>Rows are append-only. An import closes the open row and inserts the next version; nothing
 * updates {@code definition} in place.
 */
@Entity
@Table(name = "leverage_decision_tree_definition")
public class LeverageDecisionTreeDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false, length = 20)
    private LeverageFormType formType;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DefinitionStatus status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    /** Null while this version is the one in force. */
    @Column(name = "valid_to")
    private Instant validTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false, columnDefinition = "jsonb")
    private DecisionTreeDefinition definition;

    protected LeverageDecisionTreeDefinition() {
        // for Hibernate
    }

    public static LeverageDecisionTreeDefinition newVersion(DecisionTreeDefinition definition,
                                                                  Instant validFrom) {
        LeverageDecisionTreeDefinition entity = new LeverageDecisionTreeDefinition();
        entity.formType = definition.formType();
        entity.version = definition.version();
        entity.status = definition.status();
        entity.definition = definition;
        entity.validFrom = validFrom;
        entity.validTo = null;
        return entity;
    }

    /** Stamps {@code valid_to}; the JSON itself is never touched. Audit is BaseEntity's job. */
    public void close(Instant at) {
        this.validTo = at;
    }

    public Long getId() {
        return id;
    }

    public LeverageFormType getFormType() {
        return formType;
    }

    public int getVersion() {
        return version;
    }

    public DefinitionStatus getStatus() {
        return status;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public DecisionTreeDefinition getDefinition() {
        return definition;
    }
}
