package com.moba11y.ioserver;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class HbaseRepository<T extends GsonSerializable> {

    private final String TABLE_NAME;

    private final TableName tableName;

    private final String FAMILY_NAME;

    private final long MAX_CACHED_PUTS = 10000;

    private volatile long rowKey = 0;

    Connection mHbaseConnection;

    private final ScheduledExecutorService mScheduledExecuterService;

    private ScheduledFuture<?> mScheduledFuture = null;

    protected HbaseRepository(Class<? extends T> clazz)  {

        FAMILY_NAME = clazz.getSimpleName();

        TABLE_NAME = this.getClass().getSimpleName();

        tableName = TableName.valueOf(TABLE_NAME);

        try {
            mHbaseConnection = ConnectionFactory.createConnection(HBaseConfiguration.create());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Admin admin = mHbaseConnection.getAdmin()) {
            if (!admin.tableExists(tableName)) {

                HTableDescriptor tableDescriptor = new HTableDescriptor(TABLE_NAME);
                tableDescriptor.addFamily(new HColumnDescriptor(FAMILY_NAME));
                admin.createTable(tableDescriptor);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mScheduledExecuterService = Executors.newScheduledThreadPool(0);
    }

    CopyOnWriteArrayList<Put> mCachedPuts = new CopyOnWriteArrayList<>();

    protected abstract Put contsructPut(T value);

    protected abstract T constructValue(Result result);

    public void save(T value) throws IOException {

        final ByteArrayOutputStream stream = new ByteArrayOutputStream(1024);

        mCachedPuts.add(contsructPut(value));

        //The cache can only grow so large.
        if (mScheduledFuture != null && mCachedPuts.size() < MAX_CACHED_PUTS) {
            //Only cancel the current future if it's not yet running!
            mScheduledFuture.cancel(false);
        }

        //If we don't get a save call in 1 second, we flush data to table.
        mScheduledFuture = mScheduledExecuterService.schedule(this::flushValues, 1, TimeUnit.SECONDS);
    }

    public void flushValues() {

        final CopyOnWriteArrayList<Put> temp;

        //We only need to synchronize for the re-assignment of the cached puts array.
        synchronized (mCachedPuts) {
            temp = mCachedPuts;
            mCachedPuts = new CopyOnWriteArrayList<>();
        }

        try (final Table table = mHbaseConnection.getTable(tableName)) {
            table.put(temp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public T get(final byte[] rowKey) throws IOException {

        try (final Table table = mHbaseConnection.getTable(tableName)) {

            Get get = new Get(rowKey);

            return constructValue(table.get(get));
        }
    }

    public List<T> getValues() throws IOException {

        try (final Table table = mHbaseConnection.getTable(tableName)) {

            final ArrayList<T> results = new ArrayList<>();

            for (Result result : table.getScanner(new Scan())) {
                results.add(constructValue(result));
            }

            return results;
        }
    }
}
