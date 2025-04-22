package io.kunkun.tpsgenerator.request.parameter;

/**
 * Interface for sources of parameter values.
 * Implementations provide parameter values for request templates.
 */
public interface ParameterSource {

    /**
     * Gets a parameter value.
     *
     * @return the parameter value
     * @throws Exception if retrieving the value fails
     */
    String getValue() throws Exception;

    /**
     * Gets the source type.
     *
     * @return the source type
     */
    String getType();
}