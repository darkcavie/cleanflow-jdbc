package org.horus.storage.sql;

import org.horus.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractDbStorageTest {

    private MockDbStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MockDbStorage();
    }

    @Test
    void getDescription() {
        assertEquals("mockDescription", storage.getDescription());
    }

    private static class MockDbStorage extends AbstractDbStorage<String, String> {

        @Override
        public void getByKey(String key, Supplier<OutputStream> outputStream) throws StorageException {

        }

        @Override
        protected String getDescription() {
            return "mockDescription";
        }

        @Override
        protected void setKeyFields(PreparedStatement stmt, String transferKey, int pos) throws SQLException {

        }

        @Override
        protected int setFields(PreparedStatement stmt, String transfer) throws SQLException {
            return 0;
        }

        @Override
        protected String getTransfer(ResultSet rs) {
            return null;
        }
    }

}
