package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Ignore unknown properties during deserialization
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

        // ------------------------------------------------------------------ the new line
        //
        // A record's state is its COMPONENTS. A derived isXxx() is a question we ask the object,
        // not a fact it holds — but Jackson sees a bean getter, writes it out, and then refuses to
        // read it back because no component matches. Condition.isComposite() became "composite",
        // and the whole definition failed to load.
        //
        // Five more were waiting behind it: Question.isDisplayOnlyOutput(), Branch.isTerminal(),
        // DataField.isAnalystInput(), DataField.isCalculated(), Answer.isMultiPart() — the last in
        // the frozen snapshot column, where an unreadable row would be far worse.
        //
        // Record COMPONENTS are unaffected: Jackson names them from the component, which is why
        // Condition.isDefault survived as "isDefault" in the JSON that broke while isComposite()
        // came out as "composite". Round-trip test below, because that distinction is worth
        // proving rather than trusting.
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);

        return mapper;
    }

    /**
     * Hands the SAME mapper to Hibernate for every JSONB column.
     *
     * <p><b>Without this the bean above governs the REST layer only.</b> Hibernate builds its own
     * {@code ObjectMapper} for {@code JacksonJsonFormatMapper} and knows nothing about the Spring
     * context, so every setting above — the unknown-property tolerance, the JavaTimeModule, plain
     * BigDecimal notation — applied to responses and not to {@code definition} or
     * {@code responses}. That is why FAIL_ON_UNKNOWN_PROPERTIES = false did not stop the read
     * failing.
     *
     * <p>Two of those settings matter to a stored definition more than to a response.
     * WRITE_BIGDECIMAL_AS_PLAIN keeps a threshold out of scientific notation in the column, and
     * USE_BIG_DECIMAL_FOR_FLOATS stops a ratio round-tripping through a double.
     */
    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer(ObjectMapper objectMapper) {
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}

