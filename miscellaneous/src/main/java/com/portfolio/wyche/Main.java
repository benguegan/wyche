package com.portfolio.wyche;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.dalesbred.Database;
import org.h2.jdbcx.JdbcConnectionPool;

public class Main {
    public static void main(String[] args) throws Exception {
        var dataSource = JdbcConnectionPool.create("jdbc:h2:mem:wyche", "wyche", "password");
        var database = Database.forDataSource(dataSource);
        createTables(database);
    }

    private static void createTables(Database database) throws Exception {
        var path = Paths.get(Main.class.getResource("/schema.sql").toURI());
        database.update(Files.readString(path));
    }
}
