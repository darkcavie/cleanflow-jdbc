package info.cleanflow.storage.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.function.Consumer;

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

    private static class MockDbStorage extends AbstractStorage<String, String> {


        @Override
        protected String getDescription() {
            return "mockDescription";
        }

        @Override
        protected void setKeyFields(PreparedStatement stmt, String transferKey, int pos) {

        }

        @Override
        protected int setFields(PreparedStatement stmt, String transfer) {
            return 0;
        }

        @Override
        protected String getTransfer(ResultSet rs) {
            return null;
        }

        @Override
        public void update(String target) {

        }

        @Override
        public void insert(String target) {

        }

        @Override
        public boolean exist(String key) {
            return false;
        }

        @Override
        public void findAll(Consumer<String> consumer) {

        }
    }

}
