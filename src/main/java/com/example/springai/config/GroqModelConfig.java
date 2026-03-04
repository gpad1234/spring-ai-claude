package com.example.springai.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Manual configuration for the Groq {@link OpenAiChatModel}.
 *
 * <p>Spring AI 1.1.x always initialises {@code ChatCompletionRequest.extraBody}
 * to a non-null empty {@code HashMap}, and — because the class has
 * {@code @JsonInclude(NON_NULL)} — Jackson serialises it as
 * {@code "extra_body": {}}. Groq rejects this field with HTTP 400.
 *
 * <p>The fix registers a Jackson mix-in on {@link OpenAiApi.ChatCompletionRequest}
 * that marks the {@code extraBody()} accessor {@code @JsonIgnore} so neither the
 * named field ({@code "extra_body"}) nor the {@code @JsonAnyGetter} flatten path
 * ever emit output.  Only the Groq {@link WebClient} gets this custom encoder;
 * the Anthropic client is unaffected.
 *
 * <p>Defining an {@link OpenAiChatModel} bean here causes Spring AI's
 * {@code @ConditionalOnMissingBean(OpenAiChatModel.class)} check to skip the
 * auto-configured bean.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
public class GroqModelConfig {

    private static final Logger log = LoggerFactory.getLogger(GroqModelConfig.class);

    /**
     * Mix-in that suppresses the {@code extraBody} property during serialisation.
     * {@code @JsonIgnoreProperties} matches by JSON name, covering both the
     * named-field path ({@code "extra_body": {}}) and the {@code @JsonAnyGetter}
     * flatten path.  {@code @JsonIgnore} on the accessor provides a second layer.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({"extra_body"})
    abstract static class ChatCompletionRequestMixin {
        /** Belt-and-suspenders: also @JsonIgnore the accessor method. */
        @JsonIgnore
        abstract Map<String, Object> extraBody();
    }

    /**
     * Build a {@link WebClient.Builder} whose Jackson encoder does NOT include
     * the {@code extra_body} field in outbound requests.
     */
    private WebClient.Builder patchedWebClientBuilder(ObjectMapper baseMapper) {
        ObjectMapper patched = baseMapper.copy();
        // Suppress the always-empty extra_body HashMap that Groq does not support
        patched.addMixIn(OpenAiApi.ChatCompletionRequest.class, ChatCompletionRequestMixin.class);

        Jackson2JsonEncoder encoder = new Jackson2JsonEncoder(patched);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().jackson2JsonEncoder(encoder))
                .build();

        return WebClient.builder().exchangeStrategies(strategies);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            @Value("${spring.ai.openai.api-key}") String groqApiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String groqBaseUrl,
            @Value("${spring.ai.openai.chat.options.model:llama-3.3-70b-versatile}") String defaultModel,
            @Value("${spring.ai.openai.chat.options.temperature:0.7}") double temperature,
            ObjectMapper objectMapper) {

        log.info("Creating patched Groq OpenAiChatModel — extra_body suppressed via Jackson mix-in");

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(groqBaseUrl)
                .apiKey(groqApiKey)
                .webClientBuilder(patchedWebClientBuilder(objectMapper))
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(defaultModel)
                .temperature(temperature)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .build();
    }
}
