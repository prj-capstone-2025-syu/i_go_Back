package com.example.demo.entity.routine;

import com.example.demo.entity.user.User;
import jakarta.persistence.*;
import lombok.Data;
import java.util.*;

@Entity @Data
public class Routine {
    @Id @GeneratedValue
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoutineItem> items = new ArrayList<>();

    // 연관관계 편의 메서드
    public void addItem(RoutineItem item) {
        this.items.add(item);
        item.setRoutine(this);

        // 새로운 아이템은 항상 마지막에 추가 (순서 자동 설정)
        item.setOrderIndex(this.items.size() - 1);
    }
}