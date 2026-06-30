package com.store

import com.store.model.Id
import com.store.services.OrderService
import com.store.services.ProductService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class OAuthRbacSecurityTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var productService: ProductService

    @MockitoBean
    lateinit var orderService: OrderService

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `users can create and update orders`() {
        whenever(orderService.createOrder(any())).thenReturn(Id(1))

        postAsRole("/orders", "users", """{"productid":10,"count":2,"status":"pending","id":10}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))

        postAsRole("/orders/10", "users", """{"productid":10,"count":1,"status":"pending","id":10}""")
            .andExpect(status().isOk)

        verify(orderService).createOrder(any())
        verify(orderService).updateOrder(any())
        verifyNoMoreInteractions(orderService)
    }

    @Test
    fun `users cannot create or update products`() {
        postAsRole("/products", "users", """{"name":"Widget","type":"gadget","inventory":10}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        postAsRole("/products/10", "users", """{"name":"Widget","type":"gadget","inventory":10,"id":10}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        verifyNoInteractions(productService)
    }

    @Test
    fun `admins can create and update products`() {
        whenever(productService.addProduct(any())).thenReturn(Id(1))

        postAsRole("/products", "admins", """{"name":"Widget","type":"gadget","inventory":10,"id":10}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))

        postAsRole("/products/10", "admins", """{"name":"Widget","type":"gadget","inventory":10,"id":10}""")
            .andExpect(status().isOk)

        verify(productService, times(2)).addProduct(any())
        verify(productService).updateProduct(any())
        verifyNoMoreInteractions(productService)
    }

    @Test
    fun `admins cannot create or update orders`() {
        postAsRole("/orders", "admins", """{"productid":10,"count":2,"status":"pending"}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        postAsRole("/orders/10", "admins", """{"productid":10,"count":1,"status":"pending","id":10}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        verifyNoInteractions(orderService)
    }

    private fun postAsRole(path: String, role: String, body: String) =
        mockMvc.perform(
            post(path)
                .with(jwt().jwt { jwtBuilder ->
                    jwtBuilder.claim("sub", "user1")
                    jwtBuilder.claim("realm_access", mapOf("roles" to listOf(role)))
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
}
