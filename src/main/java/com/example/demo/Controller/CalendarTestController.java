package com.example.demo.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CalendarTestController {

    @GetMapping("/calendar-test")
    public String calendarTest() {
        return "calendar-test";
    }
}