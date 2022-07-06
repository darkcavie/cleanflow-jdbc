package org.horus.storage.sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Abstract base class for Transfer adapters from Database
 */
public abstract class AbstractDbAdapter {

    /**
     * Error message template
     */
    private static final String MESSAGE = "Error getting field {}";

    /**
     * Logger
     */
    private static final Logger LOG = getLogger(AbstractDbAdapter.class);

    /**
     * Wrapped Result set
     */
    private final ResultSet rs;

    /**
     * Constructor wrapping Result set
     * @param rs wrapped result set
     */
    public AbstractDbAdapter(final ResultSet rs) {
        this.rs = requireNonNull(rs, "The result set is mandatory");
    }

    /**
     * Get the object value or null, proxy method to {@link ResultSet#getObject(String)}
     * @param field field name
     * @return the value of null if exception
     */
    protected Object optObject(String field) {
        try {
            return rs.getObject(field);
        } catch (SQLException sqlException) {
            LOG.warn(MESSAGE, field, sqlException);
        }
        return null;
    }

    /**
     * Get the String value or null, proxy method to {@link ResultSet#getString(String)}
     * @param field field name
     * @return the value of null if exception
     */
    protected String optString(String field) {
        try {
            return rs.getString(field);
        } catch (SQLException sqlException) {
            LOG.warn(MESSAGE, field, sqlException);
        }
        return null;
    }
    
    protected int optInt(String field) {
        try {
            return rs.getInt(field);
        } catch (SQLException sqlException) {
            LOG.warn(MESSAGE, field, sqlException);
        }
        return 0;
    }

    protected boolean getCharAsBoolean(String field) {
        return getCharOptBoolean(field).orElse(Boolean.FALSE);
    }

    protected Boolean getCharAsThreeStateBoolean(String field) {
        return getCharOptBoolean(field).orElse(null);
    }

    private Optional<Boolean> getCharOptBoolean(String field) {
        return Optional.ofNullable(optString(field))
                .filter(not(String::isEmpty))
                .map(String::toUpperCase)
                .map(s -> s.charAt(0) == 'T');
    }

}
