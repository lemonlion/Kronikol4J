package io.kronikol.diagram.model;

/**
 * A render-ready diagram for the report. In browser-only mode (plan §3.5) {@code codeBehind} is the
 * plain PlantUML text and {@code encoded} is its compressed form for the client-side render data map;
 * no server-side image is produced.
 */
public record DiagramAsCode(String testId, String testName, String codeBehind, String encoded) {
}
