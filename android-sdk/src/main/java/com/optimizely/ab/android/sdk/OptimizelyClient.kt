/****************************************************************************
 * Copyright 2017-2021, Optimizely, Inc. and contributors                   *
 * *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                            *
 * *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 */
package com.optimizely.ab.android.sdk

import com.optimizely.ab.Optimizely
import com.optimizely.ab.config.Variation
import com.optimizely.ab.config.ProjectConfig
import kotlin.Throws
import com.optimizely.ab.UnknownEventTypeException
import com.optimizely.ab.optimizelyjson.OptimizelyJSON
import com.optimizely.ab.optimizelyconfig.OptimizelyConfig
import com.optimizely.ab.OptimizelyUserContext
import com.optimizely.ab.event.LogEvent
import com.optimizely.ab.notification.*
import org.slf4j.Logger
import java.lang.Exception
import java.util.HashMap

/**
 * The top-level container class that wraps an [Optimizely] instance.
 *
 * This proxy ensures that the Android SDK will not crash if the inner Optimizely SDK
 * fails to start. When Optimizely fails to start via [OptimizelyManager.initialize]
 * there will be no cached instance returned from [OptimizelyManager.getOptimizely].
 *
 * Accessing Optimizely through this interface eliminates the need to check for null on the reference to the Optimizely client object.
 * If the internal reference to Optimizely is null, the methods in this class will log warnings.
 */
class OptimizelyClient  /*
        OptimizelyManager is initialized with an OptimizelyClient with a null optimizely property:
        https://github.com/optimizely/android-sdk/blob/master/android-sdk/src/main/java/com/optimizely/ab/android/sdk/OptimizelyManager.java#L63
        optimizely will remain null until OptimizelyManager#initialize has been called, so isValid checks for that. Otherwise apps would crash if
        the public methods here were called before initialize.
        So, we start with an empty map of default attributes until the manager is initialized.
        */ internal constructor(private val optimizely: Optimizely?, private val logger: Logger) {
    /**
     * Returns a map of the default attributes.
     *
     * @return      The map of default attributes.
     */
    /**
     * Set default attributes to a non-null attribute map.
     * This is set by the Optimizely manager and includes things like os version and sdk version.
     *
     * @param attrs      A map of default attributes.
     */
    var defaultAttributes: Map<String, *> = HashMap<String, Any>()

    /**
     * Get the default attributes and combine them with the attributes passed in.
     * The attributes passed in take precedence over the default attributes. So, you can override default attributes.
     *
     * @param attrs      Attributes that will be combined with default attributes.
     *
     * @return           A new map of both the default attributes and attributes passed in.
     */
    private fun getAllAttributes(attrs: Map<String, *>): Map<String, *>? {
        var combinedMap: MutableMap<String, Any?>? = HashMap(defaultAttributes)

        // this essentially overrides defaultAttributes if the attrs passed in have the same key.
        if (attrs != null) {
            combinedMap!!.putAll(attrs)
        } else if (combinedMap!!.isEmpty()) {
            combinedMap = null
        }
        return combinedMap
    }

    /**
     * Activates an A/B test for a user, determines whether they qualify for the experiment, buckets a qualified
     * user into a variation, and sends an impression event to Optimizely.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/activate.
     *
     * @param experimentKey The key of the variation's experiment to activate.
     * @param userId        The user ID.
     *
     * @return              The key of the variation where the user is bucketed, or `null` if the
     * user doesn't qualify for the experiment.
     */
    fun activate(experimentKey: String,
                 userId: String): Variation? {
        return if (isValid) {
            optimizely!!.activate(experimentKey, userId, defaultAttributes)
        } else {
            logger.warn("Optimizely is not initialized, could not activate experiment {} for user {}",
                    experimentKey, userId)
            null
        }
    }

    /**
     * Activates an A/B test for a user, determines whether they qualify for the experiment, buckets a qualified
     * user into a variation, and sends an impression event to Optimizely.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/activate.
     *
     * @param experimentKey The key of the variation's experiment to activate.
     * @param userId        The user ID.
     * @param attributes    A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return              The key of the variation where the user is bucketed, or `null` if the
     * user doesn't qualify for the experiment.
     */
    fun activate(experimentKey: String,
                 userId: String,
                 attributes: Map<String, *>): Variation? {
        return if (isValid) {
            optimizely!!.activate(experimentKey, userId, getAllAttributes(attributes)!!)
        } else {
            logger.warn("Optimizely is not initialized, could not activate experiment {} for user {} " +
                    "with attributes", experimentKey, userId)
            null
        }
    }

    /**
     * Get the [ProjectConfig] instance
     *
     * @return               The current [ProjectConfig] instance.
     */
    val projectConfig: ProjectConfig?
        get() = if (isValid) {
            optimizely!!.projectConfig
        } else {
            logger.warn("Optimizely is not initialized, could not get project config")
            null
        }

    /**
     * Checks if eventHandler [EventHandler]
     * are Closeable [Closeable] and calls close on them.
     *
     * **NOTE:** There is a chance that this could be long running if the implementations of close are long running.
     */
    fun close() {
        optimizely!!.close()
    }

    /**
     * Check that this is a valid instance
     *
     * @return               True if the OptimizelyClient instance was instantiated correctly.
     */
    val isValid: Boolean
        get() = optimizely?.isValid ?: false

    /**
     * Tracks a conversion event for a user who meets the default audience conditions for an experiment.
     * When the user does not meet those conditions, events are not tracked.
     *
     * This method sends conversion data to Optimizely but doesn't return any values.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/track.
     *
     * @param eventName     The key of the event to be tracked. This key must match the event key provided when the event was created in the Optimizely app.
     * @param userId        The ID of the user associated with the event being tracked.
     */
    fun track(eventName: String,
              userId: String) {
        if (isValid) {
            try {
                optimizely!!.track(eventName, userId, defaultAttributes)
            } catch (e: Exception) {
                logger.error("Unable to track event", e)
            }
        } else {
            logger.warn("Optimizely is not initialized, could not track event {} for user {}", eventName, userId)
        }
    }

    /**
     * Tracks a conversion event for a user whose attributes meet the audience conditions for an experiment.
     * When the user does not meet those conditions, events are not tracked.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user is part of the audience that qualifies for the experiment.
     *
     * This method sends conversion data to Optimizely but doesn't return any values.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/track.
     *
     * @param eventName     The key of the event to be tracked. This key must match the event key provided when the event was created in the Optimizely app.
     * @param userId        The ID of the user associated with the event being tracked. This ID must match the user ID provided to `activate` or `isFeatureEnabled`.
     * @param attributes    A map of custom key-value string pairs specifying attributes for the user.
     */
    @Throws(UnknownEventTypeException::class)
    fun track(eventName: String,
              userId: String,
              attributes: Map<String, *>) {
        if (isValid) {
            optimizely!!.track(eventName, userId, getAllAttributes(attributes)!!)
        } else {
            logger.warn("Optimizely is not initialized, could not track event {} for user {} with attributes",
                    eventName, userId)
        }
    }

    /**
     * Tracks a conversion event for a user whose attributes meet the audience conditions for the experiment.
     * When the user does not meet those conditions, events are not tracked.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user is part of the audience that qualifies for the experiment.
     *
     * This method sends conversion data to Optimizely but doesn't return any values.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/track.
     *
     * @param eventName     The key of the event to be tracked. This key must match the event key provided when the event was created in the Optimizely app.
     * @param userId        The ID of the user associated with the event being tracked. This ID must match the user ID provided to `activate` or `isFeatureEnabled`.
     * @param attributes    A map of custom key-value string pairs specifying attributes for the user.
     * @param eventTags     A map of key-value string pairs specifying event names and their corresponding event values associated with the event.
     */
    @Throws(UnknownEventTypeException::class)
    fun track(eventName: String,
              userId: String,
              attributes: Map<String, *>,
              eventTags: Map<String?, *>) {
        if (isValid) {
            optimizely!!.track(eventName, userId, getAllAttributes(attributes)!!, eventTags)
        } else {
            logger.warn("Optimizely is not initialized, could not track event {} for user {}" +
                    " with attributes and event tags", eventName, userId)
        }
    }

    /**
     *      * Buckets a qualified user into an A/B test. Takes the same arguments and returns the same values as `activate`,
     * but without sending an impression network request. The behavior of the two methods is identical otherwise.
     * Use `getVariation` if `activate` has been called and the current variation assignment is needed for a given
     * experiment and user.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-variation.
     *
     * @param experimentKey The key of the experiment for which to retrieve the forced variation.
     * @param userId        The ID of the user for whom to retrieve the forced variation.
     *
     * @return              The key of the variation where the user is bucketed, or `null` if the user
     * doesn't qualify for the experiment.
     */
    fun getVariation(experimentKey: String,
                     userId: String): Variation? {
        return if (isValid) {
            optimizely!!.getVariation(experimentKey, userId, defaultAttributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get variation for experiment {} " +
                    "for user {}", experimentKey, userId)
            null
        }
    }

    /**
     *      * Buckets a qualified user into an A/B test. Takes the same arguments and returns the same values as `activate`,
     * but without sending an impression network request. The behavior of the two methods is identical otherwise.
     * Use `getVariation` if `activate` has been called and the current variation assignment is needed for a given
     * experiment and user.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user is part of the
     * audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-variation.
     *
     * @param experimentKey The key of the experiment for which to retrieve the variation.
     * @param userId        The ID of the user for whom to retrieve the variation.
     * @param attributes    A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return              The key of the variation where the user is bucketed, or `null` if the user
     * doesn't qualify for the experiment.
     */
    fun getVariation(experimentKey: String,
                     userId: String,
                     attributes: Map<String, *>): Variation? {
        return if (isValid) {
            optimizely!!.getVariation(experimentKey, userId, getAllAttributes(attributes)!!)
        } else {
            logger.warn("Optimizely is not initialized, could not get variation for experiment {} " +
                    "for user {} with attributes", experimentKey, userId)
            null
        }
    }

    /**
     * Forces a user into a variation for a given experiment for the lifetime of the Optimizely client.
     * The forced variation value doesn't persist across application launches.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/set-forced-variation.
     *
     * @param experimentKey  The key of the experiment to set with the forced variation.
     * @param userId         The ID of the user to force into the variation.
     * @param variationKey   The key of the forced variation.
     * Set the value to `null` to clear the existing experiment-to-variation mapping.
     *
     * @return boolean       `true` if the user was successfully forced into a variation, `false` if the `experimentKey`
     * isn't in the project file or the `variationKey` isn't in the experiment.
     */
    fun setForcedVariation(experimentKey: String,
                           userId: String,
                           variationKey: String?): Boolean {
        if (isValid) {
            return optimizely!!.setForcedVariation(experimentKey, userId, variationKey)
        } else {
            logger.warn("Optimizely is not initialized, could not set forced variation")
        }
        return false
    }

    /**
     * Returns the forced variation set by `setForcedVariation`, or `null` if no variation was forced.
     * A user can be forced into a variation for a given experiment for the lifetime of the Optimizely client.
     * The forced variation value is runtime only and doesn't persist across application launches.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/set-forced-variation.
     *
     * @param experimentKey   The key of the experiment for which to retrieve the forced variation.
     * @param userId          The ID of the user in the forced variation.
     *
     * @return                The variation the user was bucketed into, or `null` if `setForcedVariation` failed
     * to force the user into the variation.
     */
    fun getForcedVariation(experimentKey: String,
                           userId: String): Variation? {
        if (isValid) {
            return optimizely!!.getForcedVariation(experimentKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get forced variation")
        }
        return null
    }
    //======== FeatureFlag APIs ========//
    /**
     * Retrieves a list of features that are enabled for the user.
     * Invoking this method is equivalent to running `isFeatureEnabled` for each feature in the datafile sequentially.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-enabled-features.
     *
     * @param userId      The ID of the user who may have features enabled in one or more experiments.
     * @param attributes  A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return            A list of keys corresponding to the features that are enabled for the user, or an empty list if no features could be found for the specified user.
     */
    fun getEnabledFeatures(userId: String, attributes: Map<String?, *>): List<String>? {
        return if (isValid) {
            optimizely!!.getEnabledFeatures(userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get enabled feature for user {}",
                    userId)
            null
        }
    }

    /**
     * Determines whether a feature test or rollout is enabled for a given user, and
     * sends an impression event if the user is bucketed into an experiment using the feature.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/is-feature-enabled.
     *
     * @param featureKey The key of the feature to check.
     * @param userId     The ID of the user to check.
     *
     * @return           `true` if the feature is enabled, or `false` if the feature is disabled or couldn't be found.
     */
    fun isFeatureEnabled(featureKey: String,
                         userId: String): Boolean {
        return if (isValid) {
            optimizely!!.isFeatureEnabled(featureKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not enable feature {} for user {}",
                    featureKey, userId)
            false
        }
    }

    /**
     * Determines whether a feature test or rollout is enabled for a given user, and
     * sends an impression event if the user is bucketed into an experiment using the feature.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/is-feature-enabled.
     *
     * @param featureKey   The key of the feature on which to perform the check.
     * @param userId       The ID of the user on which to perform the check.
     * @param attributes   A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return             `true` if the feature is enabled, or `false` if the feature is disabled or couldn't be found.
     */
    fun isFeatureEnabled(featureKey: String,
                         userId: String,
                         attributes: Map<String?, *>): Boolean {
        return if (isValid) {
            optimizely!!.isFeatureEnabled(featureKey, userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not enable feature {} for user {} with attributes",
                    featureKey, userId)
            false
        }
    }
    //======== Feature Variables APIs ========//
    /**
     * Evaluates the specified boolean feature variable and returns its value.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * The feature key is defined from the Features dashboard, as described in
     * https://help.optimizely.com/Build_Campaigns_and_Experiments/Feature_tests%3A_Experiment_on_features.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     *
     * @return             The value of the boolean feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableBoolean(featureKey: String,
                                  variableKey: String,
                                  userId: String): Boolean? {
        return if (isValid) {
            optimizely!!.getFeatureVariableBoolean(featureKey, variableKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} boolean for user {}",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified boolean feature variable and returns its value.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     * @param attributes   A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return             The value of the boolean feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableBoolean(featureKey: String,
                                  variableKey: String,
                                  userId: String,
                                  attributes: Map<String?, *>): Boolean? {
        return if (isValid) {
            optimizely!!.getFeatureVariableBoolean(featureKey, variableKey, userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} boolean for user {} with attributes",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified double feature variable and returns its value.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     *
     * @return             The value of the double feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableDouble(featureKey: String,
                                 variableKey: String,
                                 userId: String): Double? {
        return if (isValid) {
            optimizely!!.getFeatureVariableDouble(featureKey, variableKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} double for user {}",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified double feature variable and returns its value.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     * @param attributes   A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return             The value of the double feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableDouble(featureKey: String,
                                 variableKey: String,
                                 userId: String,
                                 attributes: Map<String?, *>): Double? {
        return if (isValid) {
            optimizely!!.getFeatureVariableDouble(featureKey, variableKey, userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} double for user {} with attributes",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified integer feature variable and returns its value.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     *
     * @return             The value of the integer feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableInteger(featureKey: String,
                                  variableKey: String,
                                  userId: String): Int? {
        return if (isValid) {
            optimizely!!.getFeatureVariableInteger(featureKey, variableKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} integer for user {}",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified integer feature variable and returns its value.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     * @param attributes   A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return             The value of the integer feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableInteger(featureKey: String,
                                  variableKey: String,
                                  userId: String,
                                  attributes: Map<String?, *>): Int? {
        return if (isValid) {
            optimizely!!.getFeatureVariableInteger(featureKey, variableKey, userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} integer for user {} with attributes",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified string feature variable and returns its value.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     *
     * @return             The value of the string feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableString(featureKey: String,
                                 variableKey: String,
                                 userId: String): String? {
        return if (isValid) {
            optimizely!!.getFeatureVariableString(featureKey, variableKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} string for user {}",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Evaluates the specified string feature variable and returns its value.
     *
     * This method takes into account the user `attributes` passed in, to determine if the user
     * is part of the audience that qualifies for the experiment.
     *
     * For more information, see https://docs.developers.optimizely.com/full-stack/docs/get-feature-variable.
     *
     * @param featureKey   The key of the feature whose variable's value is being accessed.
     * @param variableKey  The key of the variable whose value is being accessed.
     * @param userId       The ID of the participant in the experiment.
     * @param attributes   A map of custom key-value string pairs specifying attributes for the user.
     *
     * @return             The value of the string feature variable, or `null` if the feature could not be found.
     */
    fun getFeatureVariableString(featureKey: String,
                                 variableKey: String,
                                 userId: String,
                                 attributes: Map<String?, *>): String? {
        return if (isValid) {
            optimizely!!.getFeatureVariableString(featureKey, variableKey, userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} string for user {} with attributes",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Get the JSON value of the specified variable in the feature.
     *
     * @param featureKey  The unique key of the feature.
     * @param variableKey The unique key of the variable.
     * @param userId      The ID of the user.
     * @return An OptimizelyJSON instance for the JSON variable value.
     * Null if the feature or variable could not be found.
     */
    fun getFeatureVariableJSON(featureKey: String,
                               variableKey: String,
                               userId: String): OptimizelyJSON? {
        return if (isValid) {
            optimizely!!.getFeatureVariableJSON(featureKey, variableKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} JSON for user {}.",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Get the JSON value of the specified variable in the feature.
     *
     * @param featureKey  The unique key of the feature.
     * @param variableKey The unique key of the variable.
     * @param userId      The ID of the user.
     * @param attributes  The user's attributes.
     * @return An OptimizelyJSON instance for the JSON variable value.
     * Null if the feature or variable could not be found.
     */
    fun getFeatureVariableJSON(featureKey: String,
                               variableKey: String,
                               userId: String,
                               attributes: Map<String?, *>): OptimizelyJSON? {
        return if (isValid) {
            optimizely!!.getFeatureVariableJSON(
                    featureKey,
                    variableKey,
                    userId,
                    attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} variable {} JSON for user {} with attributes.",
                    featureKey, variableKey, userId)
            null
        }
    }

    /**
     * Get the values of all variables in the feature.
     *
     * @param featureKey The unique key of the feature.
     * @param userId     The ID of the user.
     * @return An OptimizelyJSON instance for all variable values.
     * Null if the feature could not be found.
     */
    fun getAllFeatureVariables(featureKey: String,
                               userId: String): OptimizelyJSON? {
        return if (isValid) {
            optimizely!!.getAllFeatureVariables(featureKey, userId)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} all feature variables for user {}.",
                    featureKey, userId)
            null
        }
    }

    /**
     * Get the values of all variables in the feature.
     *
     * @param featureKey The unique key of the feature.
     * @param userId     The ID of the user.
     * @param attributes The user's attributes.
     * @return An OptimizelyJSON instance for all variable values.
     * Null if the feature could not be found.
     */
    fun getAllFeatureVariables(featureKey: String,
                               userId: String,
                               attributes: Map<String?, *>): OptimizelyJSON? {
        return if (isValid) {
            optimizely!!.getAllFeatureVariables(featureKey, userId, attributes)
        } else {
            logger.warn("Optimizely is not initialized, could not get feature {} all feature variables for user {} with attributes.",
                    featureKey, userId)
            null
        }
    }

    /**
     * Get [OptimizelyConfig] containing experiments and features map
     *
     * @return [OptimizelyConfig]
     */
    val optimizelyConfig: OptimizelyConfig?
        get() = if (isValid) {
            optimizely!!.optimizelyConfig
        } else {
            logger.error("Optimizely instance is not valid, failing getOptimizelyConfig call.")
            null
        }

    /**
     * Create a context of the user for which decision APIs will be called.
     *
     * A user context will be created successfully even when the SDK is not fully configured yet.
     *
     * @param userId The user ID to be used for bucketing.
     * @param attributes: A map of attribute names to current user attribute values.
     * @return An OptimizelyUserContext associated with this OptimizelyClient.
     */
    fun createUserContext(userId: String,
                          attributes: Map<String?, Any?>?): OptimizelyUserContext? {
        return if (optimizely != null) {
            if (attributes == null) {
                optimizely.createUserContext(userId)
            } else {
                optimizely.createUserContext(userId, attributes)
            }
        } else {
            logger.warn("Optimizely is not initialized, could not create a user context")
            null
        }
    }

    fun createUserContext(userId: String): OptimizelyUserContext? {
        return createUserContext(userId, null)
    }
    //======== Notification APIs ========//
    /**
     * Convenience method for adding DecisionNotification Handlers
     * @param handler a NotificationHandler to be added
     * @return notificationId or -1 if notification is not added
     */
    fun addDecisionNotificationHandler(handler: NotificationHandler<DecisionNotification?>?): Int {
        if (isValid) {
            return optimizely!!.addDecisionNotificationHandler(handler)
        } else {
            logger.warn("Optimizely is not initialized, could not add the notification listener")
        }
        return -1
    }

    /**
     * Convenience method for adding TrackNotification Handlers
     * @param handler a NotificationHandler to be added
     * @return notificationId or -1 if notification is not added
     */
    fun addTrackNotificationHandler(handler: NotificationHandler<TrackNotification?>?): Int {
        if (isValid) {
            return optimizely!!.addTrackNotificationHandler(handler)
        } else {
            logger.warn("Optimizely is not initialized, could not add the notification listener")
        }
        return -1
    }

    /**
     * Convenience method for adding UpdateConfigNotification Handlers
     * @param handler a NotificationHandler to be added
     * @return notificationId or -1 if notification is not added
     */
    fun addUpdateConfigNotificationHandler(handler: NotificationHandler<UpdateConfigNotification?>?): Int {
        if (isValid) {
            return optimizely!!.addUpdateConfigNotificationHandler(handler)
        } else {
            logger.warn("Optimizely is not initialized, could not add the notification listener")
        }
        return -1
    }

    /**
     * Convenience method for adding LogEvent Notification Handlers
     * @param handler a NotificationHandler to be added
     * @return notificationId or -1 if notification is not added
     */
    fun addLogEventNotificationHandler(handler: NotificationHandler<LogEvent?>?): Int {
        if (isValid) {
            return optimizely!!.addLogEventNotificationHandler(handler)
        } else {
            logger.warn("Optimizely is not initialized, could not add the notification listener")
        }
        return -1
    }

    /**
     * Return the notification center [NotificationCenter] used to add notifications for events
     * such as Activate and track.
     *
     * @return             The [NotificationCenter] or `null` if Optimizely is not initialized (or
     * initialization failed).
     */
    val notificationCenter: NotificationCenter?
        get() {
            if (isValid) {
                return optimizely!!.notificationCenter
            } else {
                logger.warn("Optimizely is not initialized, could not get the notification listener")
            }
            return null
        }
}