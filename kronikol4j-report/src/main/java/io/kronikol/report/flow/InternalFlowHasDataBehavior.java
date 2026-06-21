package io.kronikol.report.flow;

/** Whether the internal-flow link is always shown or shown on hover (.NET
 *  {@code InternalFlowHasDataBehavior}); drives the {@code window.__iflowConfig} script. */
public enum InternalFlowHasDataBehavior {
    SHOW_LINK_ON_HOVER,
    SHOW_LINK
}
