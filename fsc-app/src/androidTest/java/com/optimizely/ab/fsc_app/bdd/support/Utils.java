package com.optimizely.ab.fsc_app.bdd.support;

import com.optimizely.ab.config.EventType;
import com.optimizely.ab.config.Experiment;
import com.optimizely.ab.config.ProjectConfig;
import com.optimizely.ab.config.Variation;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Utils {
    private final static String EXP_ID = "\\{\\{#expId\\}\\}(\\S+)*\\{\\{/expId\\}\\}";
    private final static String EVENT_ID = "\\{\\{#eventId\\}\\}(\\S+)*\\{\\{/eventId\\}\\}";
    private final static String DATAFILE_PROJECT_ID = "\\{\\{datafile.projectId\\}\\}";
    private final static String EXP_CAMPAIGN_ID = "\\{\\{#expCampaignId\\}\\}(\\S+)*\\{\\{/expCampaignId\\}\\}";
    private final static String VAR_ID = "\\{\\{#varId\\}\\}(\\S+)*\\{\\{/varId\\}\\}";

    public static Object parseYAML(String args, ProjectConfig projectConfig) {
        if ("NULL".equals(args) || args.isEmpty()) {
            return null;
        }
        args = findAndReplaceAllMustacheRegex(args, projectConfig);
        Yaml yaml = new Yaml();
        return yaml.load(args);
    }

    private static String findAndReplaceAllMustacheRegex(String yaml, ProjectConfig projectConfig) {
        Pattern expIdPattern = Pattern.compile(EXP_ID);
        Matcher expIdMatcher = expIdPattern.matcher(yaml);
        while (expIdMatcher.find()) {
            Experiment experiment = Objects.requireNonNull(projectConfig).getExperimentForKey(expIdMatcher.group(1), null);
            if (experiment != null) {
                yaml = yaml.replace(expIdMatcher.group(0), experiment.getId());
            }
        }

        Pattern campaignIdPattern = Pattern.compile(EXP_CAMPAIGN_ID);
        Matcher campaignIdMatcher = campaignIdPattern.matcher(yaml);
        while (campaignIdMatcher.find()) {
            Experiment experiment = projectConfig.getExperimentForKey(campaignIdMatcher.group(1), null);
            if (experiment != null) {
                yaml = yaml.replace(campaignIdMatcher.group(0), experiment.getLayerId());
            }
        }

        Pattern eventIdPattern = Pattern.compile(EVENT_ID);
        Matcher eventIdMatcher = eventIdPattern.matcher(yaml);
        while (eventIdMatcher.find()) {
            EventType eventType = projectConfig.getEventNameMapping().get(eventIdMatcher.group(1));
            if (eventType != null) {
                yaml = yaml.replace(eventIdMatcher.group(0), eventType.getId());
            }
        }

        Pattern varIdPattern = Pattern.compile(VAR_ID);
        Matcher varIdMatcher = varIdPattern.matcher(yaml);
        while (varIdMatcher.find()) {
            String[] expVarKey = varIdMatcher.group(1).split("\\.");
            Experiment experiment = projectConfig.getExperimentForKey(expVarKey[0], null);
            if (experiment != null) {
                Variation variation = experiment.getVariationKeyToVariationMap().get(expVarKey[1]);
                if (variation != null)
                    yaml = yaml.replace(varIdMatcher.group(0), variation.getId());
            }
        }

        Pattern datafilePattern = Pattern.compile(DATAFILE_PROJECT_ID);
        Matcher datafileMatcher = datafilePattern.matcher(yaml);
        if (datafileMatcher.find()) {
            yaml = datafileMatcher.replaceAll(projectConfig.getProjectId());
        }

        return yaml;
    }

    /**
     * @param subset Object which you want to make sure that actual Object contains all its keys and values
     * @param actual Object which should contain all subset key value pairs.
     * @return True if all key value pairs of subset map exist and matches in actual object else return False.
     */
    public static Boolean containsSubset(Map<String, Object> subset, Map<String, Object> actual) {
        if (subset == null)
            return subset == actual;

        AtomicReference<Boolean> result = new AtomicReference<>(true);
        subset.forEach((key, sourceObj) -> {
            if (!actual.containsKey(key)) {
                result.set(false);
                return;
            }
            Object targetObj = actual.get(key);
            if (sourceObj instanceof Map && targetObj instanceof Map) {
                if (!containsSubset((Map) sourceObj, (Map) targetObj)) {
                    result.set(false);
                    return;
                }
            } else if (sourceObj instanceof List && targetObj instanceof List) {
                final List<Object> temp = new ArrayList<Object>((List) targetObj);
                if (!containsSubset((List) sourceObj, temp)) {
                    result.set(false);
                    return;
                }
            } else if (sourceObj instanceof String) {
                if (!sourceObj.equals(targetObj)) {
                    result.set(false);
                }
            } else if (sourceObj != targetObj)
                result.set(false);

        });
        return result.get();
    }

    private static Boolean containsSubset(final List subset, final List actual) {
        if (actual.size() != subset.size()) {
            return false;
        }
        for (int i = 0; i < subset.size(); i++) {
            if (!containsSubset((Map) subset.get(i), (Map) actual.get(i))) {
                return false;
            }
        }
        return true;
    }
}
