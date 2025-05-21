package com.example.demo.dto.routine;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoutineRequestDTO {
    private String name;
    private List<RoutineItemRequestDTO> items;
}