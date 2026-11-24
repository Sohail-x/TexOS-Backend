package com.texos.texos_backend.observability;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class DashboardSqlInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.current();
        if (measurement != null) {
            measurement.recordQuery();
        }
        return sql;
    }
}
