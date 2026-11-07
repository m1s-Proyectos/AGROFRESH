package com.agrofresh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class HomePageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageRendersDashboardAndMarksUnimplementedModulesAsPending() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Todo empieza")))
                .andExpect(content().string(containsString("Dashboard")))
                .andExpect(content().string(containsString("Usuarios")))
                .andExpect(content().string(containsString("Reportes")))
                .andExpect(content().string(containsString("aria-disabled=\"true\"")))
                .andExpect(content().string(not(containsString("Productos disponibles"))));
    }
}
