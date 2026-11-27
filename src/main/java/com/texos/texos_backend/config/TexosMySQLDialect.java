package com.texos.texos_backend.config;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.RowLockStrategy;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;

public class TexosMySQLDialect extends MySQLDialect {

    public TexosMySQLDialect() {
        super();
    }

    public TexosMySQLDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public RowLockStrategy getWriteRowLockStrategy() {
        return RowLockStrategy.NONE;
    }
}
