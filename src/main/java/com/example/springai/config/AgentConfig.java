package com.example.springai.config;

import org.springframework.context.annotation.Configuration;

/**
 * Agent configuration.
 *
 * All skills are registered as Function<Input, Output> beans with @Description annotations
 * in their respective @Configuration classes (DateTimeSkill, FileManagerSkill, etc.).
 * Spring AI auto-detects these and makes them available as tool callbacks.
 *
 * The tool function names are referenced in AgentService when building ChatClient prompts,
 * so Claude can invoke them during agentic workflows.
 *
 * Available skills:
 * - getCurrentTime: Get current date/time with timezone support
 * - calculateDateDifference: Calculate days between two dates
 * - readFile: Read files from the agent workspace
 * - writeFile: Write files to the agent workspace
 * - listFiles: List files in the agent workspace
 * - executeShellCommand: Run safe shell commands
 * - fetchUrl: Fetch content from URLs via HTTP
 * - storeMemory: Store key-value pairs in agent memory
 * - retrieveMemory: Retrieve values from agent memory
 * - listMemoryKeys: List all keys in agent memory
 * - evaluateMath: Evaluate mathematical expressions
 * - getWeather: Get current weather for any city
 */
@Configuration
public class AgentConfig {

    /**
     * All available skill/tool function bean names.
     */
    public static final String[] ALL_SKILL_NAMES = {
            "getCurrentTime",
            "calculateDateDifference",
            "readFile",
            "writeFile",
            "listFiles",
            "executeShellCommand",
            "fetchUrl",
            "storeMemory",
            "retrieveMemory",
            "listMemoryKeys",
            "evaluateMath",
            "getWeather"
    };
}
