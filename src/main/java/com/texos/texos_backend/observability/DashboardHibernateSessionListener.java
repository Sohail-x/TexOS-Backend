package com.texos.texos_backend.observability;

import org.hibernate.SessionEventListener;

public class DashboardHibernateSessionListener implements SessionEventListener {

    private long connectionAcquisitionStartedAtNanos;
    private long statementExecutionStartedAtNanos;

    @Override
    public void jdbcConnectionAcquisitionStart() {
        connectionAcquisitionStartedAtNanos = System.nanoTime();
    }

    @Override
    public void jdbcConnectionAcquisitionEnd() {
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.current();
        if (measurement != null && connectionAcquisitionStartedAtNanos != 0) {
            measurement.addConnectionAcquisitionNanos(
                    System.nanoTime() - connectionAcquisitionStartedAtNanos);
        }
        connectionAcquisitionStartedAtNanos = 0;
    }

    @Override
    public void jdbcExecuteStatementStart() {
        statementExecutionStartedAtNanos = System.nanoTime();
    }

    @Override
    public void jdbcExecuteStatementEnd() {
        DashboardRequestMeasurements.Measurement measurement =
                DashboardRequestMeasurements.current();
        if (measurement != null && statementExecutionStartedAtNanos != 0) {
            measurement.addQueryExecutionNanos(
                    System.nanoTime() - statementExecutionStartedAtNanos);
        }
        statementExecutionStartedAtNanos = 0;
    }
}
