package com.kuber.service.mapper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kuber.model.OrderDetails;
import com.kuber.model.OrderResponse;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class OrderResponseRowMapper implements RowMapper<OrderResponse> {

    @Override
    public OrderResponse mapRow(ResultSet resultSet, int i) throws SQLException {
        OrderResponse orderResponse = new OrderResponse();
        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<OrderDetails>>(){}.getType();

        orderResponse.setOrderId(resultSet.getInt("Order_Id"));
        orderResponse.setCustomerName(resultSet.getString("Customer_Name"));
        orderResponse.setStatus(resultSet.getBoolean("Status"));
        orderResponse.setOrderDate(resultSet.getTimestamp("Date"));
        orderResponse.setTotalAmount(resultSet.getInt("TotalAmount"));
        orderResponse.setOrders(gson.fromJson((String) resultSet.getObject("orders"), listType));

        return orderResponse;
    }
}
