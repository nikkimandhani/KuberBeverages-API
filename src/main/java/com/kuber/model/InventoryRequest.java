package com.kuber.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class InventoryRequest {
    private int inventoryId;

    private int productId;

    private int quantity;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date productionDate;

}
