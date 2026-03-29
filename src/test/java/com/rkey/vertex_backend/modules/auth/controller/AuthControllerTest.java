package com.rkey.vertex_backend.modules.auth.controller;
 
 import com.fasterxml.jackson.databind.ObjectMapper;
 import com.rkey.vertex_backend.core.api.ApiResponse;
 import com.rkey.vertex_backend.core.api.auth.RegistrationResponseDTO;
 import com.rkey.vertex_backend.modules.auth.model.dto.AccountVerificationDTO;
 import com.rkey.vertex_backend.modules.auth.model.dto.UserRegistrationDTO;
 import com.rkey.vertex_backend.modules.auth.service.AuthService;
 import com.rkey.vertex_backend.modules.auth.service.JwtService;
 import com.rkey.vertex_backend.modules.auth.service.TokenCacheService;
 import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
 import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
 import org.springframework.boot.test.mock.mockito.MockBean;
 import org.springframework.context.annotation.Import;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
 
 import static org.mockito.ArgumentMatchers.any;
 import static org.mockito.Mockito.when;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
 
 @WebMvcTest(AuthController.class)
 @AutoConfigureMockMvc(addFilters = false) // Disable security filters for simple endpoint testing
 class AuthControllerTest {
 
     @Autowired
     private MockMvc mockMvc;
 
     @Autowired
     private ObjectMapper objectMapper;
 
     @MockBean
     private AuthService authService;
 
     @MockBean
     private TokenCacheService tokenCacheService;
 
     @MockBean
     private JwtService jwtService;
 
     @Test
     void handleRegistration_success() throws Exception {
         UserRegistrationDTO dto = new UserRegistrationDTO(
                 "John", "Doe", "john@example.com", "johndoe", "password1234", false
         );
         
         RegistrationResponseDTO registrationResponse = new RegistrationResponseDTO("token123", "refresh123");
         ApiResponse<RegistrationResponseDTO> apiResponse = new ApiResponse<>(
                 "201" , "User profile created successfully", registrationResponse);
         
         when(authService.registerUser(any(UserRegistrationDTO.class))).thenReturn(apiResponse);
         
         mockMvc.perform(post("/api/v1/auth/register")
                 .contentType(MediaType.APPLICATION_JSON)
                 .content(objectMapper.writeValueAsString(dto)))
                 .andExpect(status().isCreated())
                 .andExpect(jsonPath("$.responseCode").value("201"))
                 .andExpect(jsonPath("$.data.accessToken").value("token123"));
     }
 
     @Test
     void handleRegistration_invalidEmail() throws Exception {
         UserRegistrationDTO dto = new UserRegistrationDTO(
                 "John", "Doe", "invalid-email", "johndoe", "password1234", false
         );
         
         mockMvc.perform(post("/api/v1/auth/register")
                 .contentType(MediaType.APPLICATION_JSON)
                 .content(objectMapper.writeValueAsString(dto)))
                 .andExpect(status().isBadRequest());
     }
 
     @Test
     void handleVerification_success() throws Exception {
         AccountVerificationDTO dto = new AccountVerificationDTO("token123", "john@example.com");
         
         ApiResponse<AccountVerificationDTO> apiResponse = new ApiResponse<>(
                 "200", "Account verified successfully", dto);
         
         when(authService.verifyAccount(any(AccountVerificationDTO.class))).thenReturn(apiResponse);
         
         mockMvc.perform(post("/api/v1/auth/verify")
                 .contentType(MediaType.APPLICATION_JSON)
                 .content(objectMapper.writeValueAsString(dto)))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.responseCode").value("200"))
                 .andExpect(jsonPath("$.message").value("Account verified successfully"));
     }
 }
