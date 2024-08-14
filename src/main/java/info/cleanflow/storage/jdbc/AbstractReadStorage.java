package info.cleanflow.storage.jdbc;

import info.cleanflow.storage.ReadStorage;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class AbstractReadStorage<K, T extends K> implements ReadStorage<K, T> {

    /**
     * SQL SELECT sentence for just one row
     */
    public static final String GET = "get";

    /**
     * Logger
     */
    private static final Logger LOG = getLogger(AbstractReadStorage.class);

    /**
     * Map with a set of SQL sentences
     */
    private Map<String, String> sentences;

    /**
     * Connection supplier
     */
    private AbstractStorage.ConnectionSupplier connectionSupplier;

    /**
     * Sentences injector setter
     * @param sentences the sentences for this Storage
     */
    public void setSentences(Map<String, String> sentences) {
        this.sentences = requireNonNull(sentences, "The sentences map is mandatory");
    }

    /**
     * Connection Supplier injector setter
     * @param connectionSupplier the connection supplier
     */
    public void setConnectionSupplier(AbstractStorage.ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = requireNonNull(connectionSupplier, "Connection supplier is mandatory");
    }

    /**
     * Description getter
     * @return a description of the stored entity
     */
    protected abstract String getDescription();

    @Override
    public void findByKey(K key, Consumer<T> consumer) {
        final String sentence;
        String message;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        checkSentences(GET);
        sentence = getSentence(GET);
        try {
            con = connectionSupplier.getConnection();
            stmt = con.prepareStatement(sentence);
            setKeyFields(stmt, key, 0);
            rs = stmt.executeQuery();
            if(rs.next()) {
                consumer.accept(getTransfer(rs));
            }
        } catch (SQLException sqlEx) {
            message = String.format("SQL Exception getting %s", key.toString());
            LOG.error(message, sqlEx);
            throw new StorageException(message, sqlEx);
        } finally {
            tryClose(rs, stmt, con);
        }
    }

    /**
     * Check each one by one if the asked sentences exists before execution
     * @param names the names of the sentences
     */
    protected void checkSentences(String... names) {
        requireNonNull(sentences, "The sentences map is required");
        for(String name : names) {
            requireNonNull(sentences.get(name), String.format("%s sentences is required", name));
        }
    }

    protected String getSentence(String name) {
        return sentences.get(name);
    }

    protected Connection getConnection() throws SQLException {
        return connectionSupplier.getConnection();
    }

    /**
     * Put the values of the fields who forms the key
     * @param stmt Prepared statement to fill
     * @param transferKey The transfer object with the values
     * @param pos The initial parameter position
     */
    protected abstract void setKeyFields(final PreparedStatement stmt, final K transferKey, final int pos)
            throws SQLException;

    /**
     * Mapper from a Result Set to a Transfer adapter
     * @param rs SQL Result set
     * @return a Transfer
     */
    protected abstract T getTransfer(ResultSet rs);

    /**
     * Jacoco does not manage very well the try resources
     * @param closable one or many closeable instances
     */
    protected void tryClose(AutoCloseable... closable) {
        if(closable == null) {
            return;
        }
        for(AutoCloseable con : closable) {
            try {
                if(con != null) {
                    con.close();
                }
            } catch (Exception ex) {
                LOG.warn("Error closing object", ex);
            }
        }
    }


}
