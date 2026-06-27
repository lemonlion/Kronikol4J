package io.kronikol.report.step;

/**
 * Configuration for {@link StepCollector} behaviour. Java port of the .NET {@code StepTrackingOptions}.
 * Immutable; build from {@link #defaults()} with the {@code with…} methods.
 *
 * @param prependKeyword                   prepend the keyword ("Given"/"When"/…) to step text in reports
 * @param inlineParameters                 include parameter values inline in step text
 * @param whenTriggersAction               a When/Then step transitions the ambient phase to Action
 * @param showStepDelimiters               insert step-delimiter hnotes into the diagram when top-level steps start
 * @param includeTrackedAssertionsInStepList tracked assertions appear as sub-steps in the step list
 */
public record StepTrackingOptions(boolean prependKeyword, boolean inlineParameters,
                                  boolean whenTriggersAction, boolean showStepDelimiters,
                                  boolean includeTrackedAssertionsInStepList) {

    /** The .NET defaults (all enabled). */
    public static StepTrackingOptions defaults() {
        return new StepTrackingOptions(true, true, true, true, true);
    }

    public StepTrackingOptions withPrependKeyword(boolean v) {
        return new StepTrackingOptions(v, inlineParameters, whenTriggersAction, showStepDelimiters,
            includeTrackedAssertionsInStepList);
    }

    public StepTrackingOptions withInlineParameters(boolean v) {
        return new StepTrackingOptions(prependKeyword, v, whenTriggersAction, showStepDelimiters,
            includeTrackedAssertionsInStepList);
    }

    public StepTrackingOptions withWhenTriggersAction(boolean v) {
        return new StepTrackingOptions(prependKeyword, inlineParameters, v, showStepDelimiters,
            includeTrackedAssertionsInStepList);
    }

    public StepTrackingOptions withShowStepDelimiters(boolean v) {
        return new StepTrackingOptions(prependKeyword, inlineParameters, whenTriggersAction, v,
            includeTrackedAssertionsInStepList);
    }

    public StepTrackingOptions withIncludeTrackedAssertionsInStepList(boolean v) {
        return new StepTrackingOptions(prependKeyword, inlineParameters, whenTriggersAction, showStepDelimiters, v);
    }
}
