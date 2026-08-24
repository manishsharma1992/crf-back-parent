package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

/**
 * A visible mandatory field reduced to what completeness needs to know.
 *
 * <p>Justification fields are represented exactly like question answers - the BA
 * confirmed they count as mandatory - so there is deliberately no field-kind
 * discriminator. If the two ever diverge, the discriminator belongs on this
 * record rather than inside the service.
 */
public record RequiredField(String key, String rawValue) {

    public boolean isAnswered() {
        return rawValue != null && !rawValue.isBlank();
    }
}
