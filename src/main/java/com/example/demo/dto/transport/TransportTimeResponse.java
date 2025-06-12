package com.example.demo.dto.transport;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportTimeResponse {
    private Integer walking;    // 도보 이동시간 (분)
    private Integer driving;    // 자차 이동시간 (분)
    private Integer transit;    // 대중교통 이동시간 (분)
}
