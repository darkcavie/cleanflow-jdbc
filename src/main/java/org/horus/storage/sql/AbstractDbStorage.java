package org.horus.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Consumer;

import org.horus.storage.NotFoundStorageException;
import org.horus.storage.Storage;
import org.horus.storage.StorageException;
import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Utility abstract class for JDBC Storage based in Transfer objects
 * @param <K> Key transfer type
 * @param <T> Transfer type to storage
 */
public abstract class AbstractDbStorage<K, T> implements Storage<K, T> {

    /**
     * SQL SELECT sentence to know if a key exists
     */
    public static final String EXISTS = "exist";

    /**
     * SQL INSERT sentence to insert just one row
     */
    public static final String INSERT = "insert";

    /**
     * SQL UPDATE sentence to update just one row
     */
    public static final String UPDATE = "update";

    /**
     * SQL DELETE sentence to delete just one row
     */
    public static final String DELETE = "delete";

    /**
     * SQL SELECT sentence for just one row
     */
    public static final String GET = "get";

    /**
     * Logger
     */
    private static final Logger LOG = getLogger(AbstractDbStorage.class);

    /**
     * Map with a set of SQL sentences
     */
    private Map<String, String> sentences;

    /**
     * Connection supplier
     */
    private ConnectionSupplier connectionSupplier;

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
    public void setConnectionSupplier(ConnectionSupplier connectionSupplier) {
        this.connectionSupplier = requireNonNull(connectionSupplier, "Connection supplier is mandatory");
    }

    /**
     * Store a transfer object in db
     * @param key Key
     * @param transfer Transfer object to store
     * @throws StorageException In case of a SQL failure
     */
    @Override
    public void upsert(K key, T transfer) throws StorageException {
        final String sentence;
        final String checkSentence;
        Connection con = null;

        checkSentences(EXISTS, INSERT, UPDATE);
        try {
            con = connectionSupplier.getConnection();
            con.setAutoCommit(false);
            //Insert or Update
            checkSentence = sentences.get(EXISTS);
            sentence = checkExist(con.prepareStatement(checkSentence), key);
            //Doing upsert
            executeUpsert(con.prepareStatement(sentence), transfer, key);
            con.commit();
        } catch(SQLException sqlEx) {
            LOG.error("SQL Exception putting {}", transfer, sqlEx);
            throw new StorageException("Storage error putting " + getDescription(), sqlEx);
        } finally {
            tryClose(con);
        }
    }

    @Override
    public void getByKey(K key, Consumer<T> consumer) throws StorageException {
        final String sentence;
        String message;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        checkSentences(GET);
        sentence = sentences.get(GET);
        try {
            con = connectionSupplier.getConnection();
            stmt = con.prepareStatement(sentence);
            setKeyFields(stmt, key, 0);
            rs = stmt.executeQuery();
            if(rs.next()) {
                consumer.accept(getTransfer(rs));
            } else {
                message = String.format("Not found result for key: %s", key);
                throw new NotFoundStorageException(message);
            }
        } catch (SQLException sqlEx) {
            message = String.format("SQL Exception getting %s", key.toString());
            LOG.error(message, sqlEx);
            throw new StorageException(message, sqlEx);
        } finally {
            tryClose(rs, stmt, con);
        }
    }

    @Override
    public void deleteByKey(K key) throws StorageException {
        final String sentence;
        Connection con = null;
        PreparedStatement stmt = null;
        String message;

        checkSentences(DELETE);
        sentence = sentences.get(DELETE);
        try {
            con = connectionSupplier.getConnection();
            stmt = con.prepareStatement(sentence);
            setKeyFields(stmt, key, 0);
            stmt.executeUpdate();
        } catch (SQLException sqlEx) {
            message = String.format("SQL Exception deleting %s", getDescription());
            LOG.error("SQL Exception deleting {}", key, sqlEx);
            throw new StorageException(message, sqlEx);
        } finally {
            tryClose(stmt, con);
        }
    }

    /**
     * Description getter
     * @return a description of the stored entity
     */
    protected abstract String getDescription();

    /**
     * Put the values of the fields who forms the key
     * @param stmt Prepared statement to fill
     * @param transferKey The transfer object with the values
     * @param pos The initial parameter position
     * @throws SQLException from the set method called
     */
    protected abstract void setKeyFields(final PreparedStatement stmt, final K transferKey, final int pos)
            throws SQLException;

    /**
     * Put the values of the fields who does not form part of the key
     * @param stmt Prepared statement to fill
     * @param transfer The transfer object with the values
     * @return The final parameter position
     * @throws SQLException from the set method called
     */
    protected abstract int setFields(final PreparedStatement stmt, final T transfer)
            throws SQLException;

    /**
     * Mapper from a Result Set to a Transfer adapter
     * @param rs SQL Result set
     * @return a Transfer
     */
    protected abstract T getTransfer(ResultSet rs);

    /**
     * Determines which sentence use, INSERT or UPDATE, checking the exist
     * @param countStmt Count statement
     * @param key Key object
     * @return The proper sentence
     * @throws SQLException consulting the ResultSet
     * @throws StorageException if there is no result
     */
    protected String checkExist(PreparedStatement countStmt, final K key) throws SQLException,
            StorageException {
        ResultSet rs = null;

        try {
            setKeyFields(countStmt, key, 0);
            rs = countStmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0 ? sentences.get(INSERT) : sentences.get(UPDATE);
            } else {
                throw new StorageException("Error confirming existing ");
            }
        } finally {
            tryClose(countStmt, rs);
        }
    }

    /**
     * Execute a Update or Insert type sentence
     * @param upsertStmt statement
     * @param transfer value object
     * @param key key
     * @throws SQLException from the set fields methods
     */
    protected void executeUpsert(PreparedStatement upsertStmt, T transfer, K key) throws SQLException {
        int i;

        try {
            i = setFields(upsertStmt, transfer);
            setKeyFields(upsertStmt, key, i);
            upsertStmt.executeUpdate();
        } finally {
            tryClose(upsertStmt);
        }
    }

    protected void list(String sentenceName, ParameterFiller parameterFiller, ResultSetHandler resultSetHandler)
            throws StorageException {
        final String sentence;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        checkSentences(sentenceName);
        sentence = sentences.get(sentenceName);
        try {
            con = connectionSupplier.getConnection();
            stmt = con.prepareStatement(sentence);
            parameterFiller.fillStatement(stmt);
            rs = stmt.executeQuery();
            resultSetHandler.handleResult(rs);
        } catch (SQLException sqlException) {
            LOG.error("SQL exception trying execute sentence {}", sentence, sqlException);
            throw new StorageException("Error listing " + sentence, sqlException);
        } finally {
            tryClose(rs, stmt, con);
        }
    }

    protected void listToConsumer(String sentence, Consumer<T> consumer, ParameterFiller parameterFiller)
            throws StorageException {
        list(sentence, parameterFiller, rs -> {
            while(rs.next()) {
                final T transfer;

                transfer = getTransfer(rs);
                consumer.accept(transfer);
            }
        });
    }

    protected void executeUpdate(String sentenceName, ParameterFiller parameterFiller) throws StorageException {
        final String sentence;
        Connection con = null;
        PreparedStatement stmt = null;

        checkSentences(sentenceName);
        sentence = sentences.get(sentenceName);
        try {
            con = connectionSupplier.getConnection();
            stmt = con.prepareStatement(sentence);
            parameterFiller.fillStatement(stmt);
            stmt.executeUpdate();
        } catch (SQLException sqlException) {
            LOG.error("SQL Exception executing update {}", sentence, sqlException);
            throw new StorageException("Storage exception executing " + sentenceName, sqlException);
        } finally {
            tryClose(stmt, con);
        }
    }


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

    /**
     * Connection supplier
     */
    @FunctionalInterface
    public interface ConnectionSupplier {

        /**
         * Method getting a Database connection
         * @return The opened connection
         * @throws SQLException in any database error
         */
        Connection getConnection() throws SQLException;

    }

    @FunctionalInterface
    protected interface ParameterFiller {

        void fillStatement(PreparedStatement stmt) throws SQLException;

    }

    @FunctionalInterface
    protected interface ResultSetHandler {

        void handleResult(ResultSet rs) throws SQLException;

    }

}
