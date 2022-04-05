package com.kuber.service;

import com.kuber.Authentication.JwtUtils;
import com.kuber.model.OrderDetailsDictionary;
import com.kuber.model.OrderResponse;
import com.kuber.model.OrdersRequest;
import com.kuber.model.AssignHistoryResponse;
import com.kuber.service.mapper.AssigneeHistoryRowMapper;
import com.kuber.service.mapper.OrderResponseRowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.webjars.NotFoundException;

import java.sql.SQLException;
import java.util.*;

import static java.lang.Boolean.parseBoolean;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class OrdersService {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static Logger LOGGER = LoggerFactory.getLogger(OrdersService.class);

    private static final String INSERT_ORDER = "INSERT INTO Orders(Customer_Name, TotalAmount, Date, Bill_No, Created_By, Created_Date) VALUES ( :customerName, :totalAmount, :orderDate, :billNo, :createdBy, :createdDate)";
    private static final String INSERT_ORDER_DETAILS = "INSERT INTO Order_Details(Order_Id, Product_Id, Quantity, Rate, Amount, Created_By, Created_Date) VALUES ( :orderId, :productId, :quantity, :rate, :amount, :createdBy, :createdDate)";
    private static final String GET_LAST_INSERTED_ORDER_ID = "SELECT LAST_INSERT_ID()";


    private static final String UPDATE_ORDER = "UPDATE Orders set TotalAmount=:totalAmount, Updated_By=:updatedBy, Updated_Date=:updatedDate where Order_Id=:orderId";
    private static final String UPDATE_ORDER_DETAILS = "UPDATE Order_Details set Product_Id=:productId, Quantity=:quantity, Rate=:rate, Amount=:amount, Updated_By=:updatedBy, Updated_Date=:updatedDate where Order_Details_Id=:orderDetailsId";
    private static final String GET_ORDER_BY_ID = "SELECT count(*) FROM Orders where Order_Id=:orderId";
    private static final String DELETE_ORDER = "DELETE FROM Orders where Order_Id=:orderId";
    private static final String ASSIGN_ORDER = "UPDATE Orders set Assignee_Name=:assigneeName, Assigned_Updated_Date=:assignedUpdatedDate, Assigned_Status=:assignedStatus, Assigned_By=:assignedBy where Order_Id=:orderId";
    private static final String GET_ORDER_ASSIGNEE_HISTORY = "SELECT * FROM Assignee_History where Order_Id=:orderId order by Order_Id desc";

    private String GET_ORDERS = "Select orders.*, orders.TotalAmount - sum(case when ph.totalReceived is null then 0 else ph.totalReceived end) as Balance_Due, od.orders\n" +
            " from Orders orders\n" +
            " left join (SELECT CASE WHEN sum(Received_Payment) IS NULL THEN 0 ELSE sum(Received_Payment) END AS totalReceived, Order_Id \n" +
            " FROM Payment_History GROUP BY Order_Id) ph on orders.Order_Id = ph.Order_Id\n" +
            " left join (SELECT Order_Id, JSON_ARRAYAGG(JSON_OBJECT('orderDetailsId', o.Order_Details_Id, 'productId', p.Product_Id , 'quantity', o.Quantity, 'rate', o.Rate, 'productType', p.Product_Type, 'amount', o.Amount )) as orders FROM Order_Details o left join Product p on p.Product_Id = o.Product_Id  GROUP BY o.Order_Id) od \n" +
            " on orders.Order_Id = od.Order_Id";

    @Transactional
    public String addOrder(OrdersRequest orderRequest, String token) throws SQLException, Exception {
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        String userName = jwtUtils.getUserNameFromJwtToken(token);

        parameters.addValue("customerName", orderRequest.getCustomerName());
        parameters.addValue("totalAmount", orderRequest.getTotalAmount());
        parameters.addValue("orderDate", orderRequest.getOrderDate());
        parameters.addValue("billNo", orderRequest.getBillNo());
        parameters.addValue("createdBy", userName);
        parameters.addValue("createdDate", new Date());
        int rowsAffected = this.namedParameterJdbcTemplate.update(INSERT_ORDER, parameters);
        if (rowsAffected != 1) {
            throw new SQLException("Failed to insert Order: " + orderRequest.toString());
        }
        int lastInsertedOrderId = this.namedParameterJdbcTemplate.queryForObject(GET_LAST_INSERTED_ORDER_ID, parameters, Integer.class);


        MapSqlParameterSource orderDetailsParameters = new MapSqlParameterSource();
        orderDetailsParameters.addValue("orderId", lastInsertedOrderId);
        for (OrderDetailsDictionary orderDetails : orderRequest.getOrders()) {
            orderDetailsParameters.addValue("productId", orderDetails.getProductId());
            orderDetailsParameters.addValue("quantity", orderDetails.getQuantity());
            orderDetailsParameters.addValue("rate", orderDetails.getRate());
            orderDetailsParameters.addValue("amount", orderDetails.getAmount());
            orderDetailsParameters.addValue("createdBy", userName);
            orderDetailsParameters.addValue("createdDate", new Date());
            int orderDetailsRowsAffected = this.namedParameterJdbcTemplate.update(INSERT_ORDER_DETAILS, orderDetailsParameters);
            if (orderDetailsRowsAffected != 1) {
                throw new SQLException("Failed to insert OrderDetails: " + orderRequest.toString());
            }
        }
        return "Order successfully added";
    }

    @Transactional
    public String updateOrder(OrdersRequest orderRequest, String token) throws SQLException {
        SqlParameterSource namedParameters = new BeanPropertySqlParameterSource(orderRequest);
        int count = this.namedParameterJdbcTemplate.queryForObject(GET_ORDER_BY_ID, namedParameters, Integer.class);
        if (count == 0) {
            throw new NotFoundException("Order Not Found");
        }
        String userName = jwtUtils.getUserNameFromJwtToken(token);
        if (orderRequest.isAssignUpdate()) {
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("orderId", orderRequest.getOrderId());
            parameters.addValue("assigneeName", orderRequest.getAssigneeName());
            parameters.addValue("assignedUpdatedDate", orderRequest.getAssignedUpdatedDate());
            parameters.addValue("assignedStatus", orderRequest.isAssignedStatus());
            parameters.addValue("assignedBy", userName);

            int rowsAffected = this.namedParameterJdbcTemplate.update(ASSIGN_ORDER, parameters);

            if (rowsAffected != 1) {
                throw new SQLException("Failed to assign Order: " + orderRequest.toString());
            }
            return "Order Assigned Successfully";
        } else {
            MapSqlParameterSource orderParameters = new MapSqlParameterSource();
            orderParameters.addValue("orderId", orderRequest.getOrderId());
            orderParameters.addValue("totalAmount", orderRequest.getTotalAmount());
            orderParameters.addValue("updatedBy", userName);
            orderParameters.addValue("updatedDate", new Date());
            int rowsAffected = this.namedParameterJdbcTemplate.update(UPDATE_ORDER, orderParameters);

            if (rowsAffected != 1) {
                throw new SQLException("Failed to update Order: " + orderRequest.toString());
            }

            for (OrderDetailsDictionary orderDetailsDictionary : orderRequest.getOrders()) {
                MapSqlParameterSource orderDetailsParameters = new MapSqlParameterSource();
                orderDetailsParameters.addValue("orderDetailsId", orderDetailsDictionary.getOrderDetailsId());
                orderDetailsParameters.addValue("productId", orderDetailsDictionary.getProductId());
                orderDetailsParameters.addValue("quantity", orderDetailsDictionary.getQuantity());
                orderDetailsParameters.addValue("rate", orderDetailsDictionary.getRate());
                orderDetailsParameters.addValue("amount", orderDetailsDictionary.getAmount());
                orderDetailsParameters.addValue("updatedBy", userName);
                orderDetailsParameters.addValue("updatedDate",  new Date());
                this.namedParameterJdbcTemplate.update(UPDATE_ORDER_DETAILS, orderDetailsParameters);
            }
            return "Order successfully updated";
        }

    }

    public List<OrderResponse> getOrders(Map<String, String> params) throws SQLException {

            StringBuilder queryString = new StringBuilder();
            Map<String, String> columnMap = new HashMap<>();
            columnMap.put("billNo", "Bill_No");
            columnMap.put("customerName", "Customer_Name");
            columnMap.put("orderDate", "Date");
            columnMap.put("assignedStatus", "Assigned_Status");
            columnMap.put("assigneeName", "Assignee_Name");
            columnMap.put("updatedDate", "Assigned_Updated_Date");

            queryString.append(GET_ORDERS);
            queryString.append(" WHERE 1=1 ");
            if(!params.isEmpty()){
                for (String key : params.keySet()) {
                    String value = params.get(key);
                    if (!key.equalsIgnoreCase("orderStatus") && isNotBlank(value)) {
                        queryString.append(" AND " + columnMap.get(key) + "=:" + key);
                    }
                }
            }

            queryString.append(" group by orders.Order_Id order by orders.Order_Id desc");
            try {
                MapSqlParameterSource parameters = new MapSqlParameterSource(params);
                parameters.addValue("assignedStatus", parseBoolean(params.get("assignedStatus")));
                return this.namedParameterJdbcTemplate.query(queryString.toString(), parameters, new OrderResponseRowMapper());
            } catch (Exception e) {
                throw new SQLException("SQL Error ", e);
            }

    }

    @Transactional
    public String deleteOrder(String orderId, String token) throws SQLException {
        Collection<? extends GrantedAuthority> roles = jwtUtils.getRolesFromJwtToken(token);
        boolean isAdmin = false;
        for (GrantedAuthority grantedAuthority : roles) {
            if ((grantedAuthority.getAuthority()).equals("Admin") || (grantedAuthority.getAuthority()).equals("Super_Admin")) {
                isAdmin = true;
            }
        }

        if (!isAdmin) {
            throw new AuthorizationServiceException("User not authorized to delete");
        } else {
            MapSqlParameterSource parameterSource = new MapSqlParameterSource();
            parameterSource.addValue("orderId", orderId);
            int count = this.namedParameterJdbcTemplate.queryForObject(GET_ORDER_BY_ID, parameterSource, Integer.class);
            if (count == 0) {
                throw new NotFoundException("Order Not Found");
            }

            int rowsAffected = this.namedParameterJdbcTemplate.update(DELETE_ORDER, parameterSource);
            if (rowsAffected == 0) {
                throw new SQLException("Failed to delete Order: " + orderId);
            }
            return "Order Deleted Successfully";
        }

    }

    public List<AssignHistoryResponse> getOrderAssigneeHistory(int orderId) throws SQLException {
        try {
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("orderId", orderId);
            return this.namedParameterJdbcTemplate.query(GET_ORDER_ASSIGNEE_HISTORY, parameters, new AssigneeHistoryRowMapper());
        } catch (Exception e) {
            throw new SQLException("SQL Error ", e);
        }
    }
}

