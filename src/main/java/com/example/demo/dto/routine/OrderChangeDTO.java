package com.example.demo.dto.routine;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderChangeDTO {
    private Long itemId;
    private int newPosition;
}