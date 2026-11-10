package com.agrofresh.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import com.agrofresh.service.CategoriaService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityLoginTest {

    private static final String TEST_USERNAME = "mock-login-user";
    private static final String TEST_PASSWORD = "mock-only-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AgroFreshUserDetailsService userDetailsService;

    @MockBean
    private LoginAttemptService intentos;

    @MockBean
    private PermisoService permisos;

    @MockBean
    private CategoriaService categoriaService;

    @BeforeEach
    void provideIsolatedUser() {
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(user(false, false));
    }

    @Test
    void acceptsThePasswordMatchingAnInMemoryBcryptFixture() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", TEST_USERNAME)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(intentos).registrarExito(TEST_USERNAME);
        verify(intentos, never()).registrarFallo(TEST_USERNAME);
    }

    @Test
    void rejectsAnIncorrectPasswordAndRecordsTheAttemptThroughTheMock() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", TEST_USERNAME)
                        .param("password", "incorrect-test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        verify(intentos).registrarFallo(TEST_USERNAME);
        verify(intentos, never()).registrarExito(TEST_USERNAME);
    }

    @Test
    void loginPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", TEST_USERNAME)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().isForbidden());

        verify(userDetailsService, never()).loadUserByUsername(TEST_USERNAME);
    }

    @Test
    void doesNotCountDisabledOrLockedAccountsAsBadPasswordAttempts() throws Exception {
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(user(true, false));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", TEST_USERNAME)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        verify(intentos, never()).registrarFallo(TEST_USERNAME);

        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(user(false, true));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", TEST_USERNAME)
                        .param("password", TEST_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        verify(intentos, never()).registrarFallo(TEST_USERNAME);
    }

    private UserDetails user(boolean disabled, boolean locked) {
        return User.withUsername(TEST_USERNAME)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .disabled(disabled)
                .accountLocked(locked)
                .authorities("ROLE_ADMIN")
                .build();
    }
}
