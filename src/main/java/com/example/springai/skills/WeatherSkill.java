package com.example.springai.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;

/**
 * Weather skill for the AI agent.
 * Uses the free Open-Meteo API (no API key required).
 * Geocodes the city name, then fetches current weather conditions.
 */
@Configuration
public class WeatherSkill {

    private static final Logger log = LoggerFactory.getLogger(WeatherSkill.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record WeatherRequest(String city) {}

    public record WeatherResponse(
            String city,
            double latitude,
            double longitude,
            double temperatureCelsius,
            double temperatureFahrenheit,
            double windspeedKph,
            String condition,
            boolean success,
            String error
    ) {}

    @Bean
    @Description("Get the current weather for any city in the world. Returns temperature (Celsius and Fahrenheit), wind speed, and a short weather condition description. Example cities: 'New York', 'London', 'Tokyo'.")
    public Function<WeatherRequest, WeatherResponse> getWeather() {
        return request -> {
            log.info("Skill invoked: getWeather({})", request.city());
            try {
                // Step 1: Geocode the city name → lat/lon
                String encodedCity = URLEncoder.encode(request.city().trim(), StandardCharsets.UTF_8);
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + encodedCity + "&count=1&language=en&format=json";

                String geoBody = get(geoUrl);
                JsonNode geoJson = objectMapper.readTree(geoBody);
                JsonNode results = geoJson.path("results");

                if (!results.isArray() || results.isEmpty()) {
                    return error(request.city(), "City not found: '" + request.city() + "'");
                }

                JsonNode place = results.get(0);
                double lat = place.path("latitude").asDouble();
                double lon = place.path("longitude").asDouble();
                String resolvedName = place.path("name").asText(request.city())
                        + ", " + place.path("country").asText("");

                // Step 2: Fetch current weather
                String weatherUrl = String.format(
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&current_weather=true&wind_speed_unit=kmh",
                        lat, lon);

                String weatherBody = get(weatherUrl);
                JsonNode weatherJson = objectMapper.readTree(weatherBody);
                JsonNode current = weatherJson.path("current_weather");

                double tempC = current.path("temperature").asDouble();
                double tempF = tempC * 9.0 / 5.0 + 32;
                double windKph = current.path("windspeed").asDouble();
                int wmCode = current.path("weathercode").asInt(-1);
                String condition = describeWMO(wmCode);

                log.info("Weather for {}: {}°C, {}, wind {}km/h", resolvedName, tempC, condition, windKph);

                return new WeatherResponse(resolvedName, lat, lon,
                        Math.round(tempC * 10.0) / 10.0,
                        Math.round(tempF * 10.0) / 10.0,
                        windKph, condition, true, null);

            } catch (Exception e) {
                log.error("getWeather failed for '{}': {}", request.city(), e.getMessage());
                return error(request.city(), e.getMessage());
            }
        };
    }

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "SpringAI-Agent/1.0")
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private WeatherResponse error(String city, String message) {
        return new WeatherResponse(city, 0, 0, 0, 0, 0, null, false, message);
    }

    /**
     * Maps WMO Weather interpretation codes to human-readable descriptions.
     * https://open-meteo.com/en/docs#weathervariables
     */
    private String describeWMO(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 71 -> "Slight snow";
            case 73 -> "Moderate snow";
            case 75 -> "Heavy snow";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown (code " + code + ")";
        };
    }
}
