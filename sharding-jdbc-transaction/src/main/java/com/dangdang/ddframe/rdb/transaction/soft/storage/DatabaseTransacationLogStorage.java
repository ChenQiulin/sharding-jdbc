/**
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.transaction.soft.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.dangdang.ddframe.rdb.transaction.soft.api.SoftTransactionConfiguration;
import com.dangdang.ddframe.rdb.transaction.soft.api.SoftTransactionType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于数据库的事务日志存储器接口.
 * 
 * @author zhangliang
 */
@RequiredArgsConstructor
@Slf4j
public final class DatabaseTransacationLogStorage implements TransacationLogStorage {
    
    private final SoftTransactionConfiguration transactionConfiguration;
    
    private final SoftTransactionConfiguration transactionConfig;
    
    @Override
    public void add(final TransactionLog transactionLog) {
        String sql = "INSERT INTO `transaction_log` (`id`, `transaction_type`,`data_source`,`sql`,`parameters`) VALUES (?, ?, ?, ?, ?);";
        try (
                Connection conn = transactionConfiguration.getTransactionLogDataSource().getConnection();
                PreparedStatement psmt = conn.prepareStatement(sql)) {
            psmt.setString(1, transactionLog.getId());
            psmt.setString(2, transactionLog.getTransactionType().toString());
            psmt.setString(3, transactionLog.getDataSource());
            psmt.setString(4, transactionLog.getSql());
            psmt.setString(5, new Gson().toJson(transactionLog.getParameters()));
            psmt.executeUpdate();
        } catch (final SQLException ex) {
            log.error("Save transaction log error:", ex);
        }
    }
    
    @Override
    public TransactionLog load(final String id) {
        String sql = "SELECT `id`, `transaction_type`, `data_source`, `sql`, `parameters`, `async_delivery_try_times` FROM `transaction_log` WHERE `id`=?;";
        try (
                Connection conn = transactionConfiguration.getTransactionLogDataSource().getConnection();
                PreparedStatement psmt = conn.prepareStatement(sql)) {
            psmt.setString(1, id);
            try (ResultSet rs = psmt.executeQuery()) {
                if (rs.next()) {
                    Gson gson = new Gson();
                    List<Object> parameters = gson.fromJson(rs.getString(5), new TypeToken<List<Object>>() { }.getType());
                    TransactionLog result = new TransactionLog(
                            rs.getString(1), "", SoftTransactionType.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4), parameters, rs.getInt(6));
                    return result;
                }
            }
        } catch (final SQLException ex) {
            log.error("Query transaction log error:", ex);
        }
        throw new IllegalArgumentException(String.format("Cannot found transaction log for id: %s", id));
    }
    
    @Override
    public List<TransactionLog> loadBatch(final String transactionId) {
        throw new UnsupportedOperationException();
    }
   
    @Override
    public void remove(final String id) {
        String sql = "DELETE FROM `transaction_log` WHERE `id`=?;";
        try (
                Connection conn = transactionConfiguration.getTransactionLogDataSource().getConnection();
                PreparedStatement psmt = conn.prepareStatement(sql)) {
            psmt.setString(1, id);
            psmt.executeUpdate();
        } catch (final SQLException ex) {
            log.error("Delete transaction log error:", ex);
        }
    }
    
    @Override
    public void removeBatch(final String transactionId) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public List<TransactionLog> findAllForLessThanMaxAsyncProcessTimes(final int size) {
        List<TransactionLog> result = new ArrayList<>(size);
        String sql = "SELECT `id`, `transaction_type`, `data_source`, `sql`, `parameters`, `async_delivery_try_times` FROM `transaction_log` WHERE `async_delivery_try_times`<? LIMIT ?;";
        try (
                Connection conn = transactionConfiguration.getTransactionLogDataSource().getConnection();
                PreparedStatement psmt = conn.prepareStatement(sql)) {
            psmt.setInt(1, transactionConfig.getAsyncMaxDeliveryTryTimes());
            psmt.setInt(2, size);
            try (ResultSet rs = psmt.executeQuery()) {
                while (rs.next()) {
                    Gson gson = new Gson();
                    List<Object> parameters = gson.fromJson(rs.getString(5), new TypeToken<List<Object>>() { }.getType());
                    result.add(new TransactionLog(rs.getString(1), "", SoftTransactionType.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4), parameters, rs.getInt(6)));
                }
            }
        } catch (final SQLException ex) {
            log.error("Find all transaction log error:", ex);
        }
        return result;
    }
    
    @Override
    public void increaseAsyncDeliveryTryTimes(final String id) {
        String sql = "UPDATE `transaction_log` SET `async_delivery_try_times`=`async_delivery_try_times`+1 WHERE `id`=?;";
        try (
                Connection conn = transactionConfiguration.getTransactionLogDataSource().getConnection();
                PreparedStatement psmt = conn.prepareStatement(sql)) {
            psmt.setString(1, id);
            psmt.executeUpdate();
        } catch (final SQLException ex) {
            log.error("Update transaction log error:", ex);
        }
    }
}
