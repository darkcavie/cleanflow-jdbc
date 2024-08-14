package info.cleanflow.storage.jdbc;

import info.cleanflow.storage.Storage;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Utility abstract class for JDBC Storage based in Transfer objects
 * @param <K> Key transfer type
 * @param <T> Transfer type to storage
 */
public abstract class AbstractStorage<K, T extends K> extends AbstractReadStorage<K, T> implements Storage<K, T> {

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
     * Logger
     */
    private static final Logger LOG = getLogger(AbstractStorage.class);

    /**
     * Store a transfer object in db
     * @param transfer Transfer object to store
     * @throws StorageException In case of a SQL failure
     */
    @Override
    public void upsert(T transfer) throws StorageException {
        final String sentence;
        final String checkSentence;
        Connection con = null;

        checkSentences(EXISTS, INSERT, UPDATE);
        try {
            con = getConnection();
            con.setAutoCommit(false);
            //Insert or Update
            checkSentence = getSentence(EXISTS);
            sentence = checkExist(con.prepareStatement(checkSentence), transfer);
            //Doing upsert
            executeUpsert(con.prepareStatement(sentence), transfer);
            con.commit();
        } catch(SQLException sqlEx) {
            LOG.error("SQL Exception putting {}", transfer, sqlEx);
            throw new StorageException("Storage error putting " + getDescription(), sqlEx);
        } finally {
            tryClose(con);
        }
    }

    @Override
    public void deleteByKey(K key) throws StorageException {
        final String sentence;
        Connection con = null;
        PreparedStatement stmt = null;
        String message;

        checkSentences(DELETE);
        sentence = getSentence(DELETE);
        try {
            con = getConnection();
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
     * Put the values of the fields who does not form part of the key
     * @param stmt Prepared statement to fill
     * @param transfer The transfer object with the values
     * @return The final parameter position
     * @throws SQLException from the set method called
     */
    protected abstract int setFields(final PreparedStatement stmt, final T transfer)
            throws SQLException;

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
        final String sentenceName;
        ResultSet rs = null;

        try {
            setKeyFields(countStmt, key, 0);
            rs = countStmt.executeQuery();
            if (rs.next()) {
                sentenceName = rs.getInt(1) == 0 ? INSERT : UPDATE;
                return getSentence(sentenceName);
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
     * @throws SQLException from the set fields methods
     */
    protected void executeUpsert(PreparedStatement upsertStmt, T transfer) throws SQLException {
        int i;

        try {
            i = setFields(upsertStmt, transfer);
            setKeyFields(upsertStmt, transfer, i);
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
        sentence = getSentence(sentenceName);
        try {
            con = getConnection();
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
        sentence = getSentence(sentenceName);
        try {
            con = getConnection();
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
