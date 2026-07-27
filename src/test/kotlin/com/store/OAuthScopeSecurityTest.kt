package com.store

import com.store.model.Id
import com.store.services.OrderService
import com.store.services.ProductService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
class OAuthScopeSecurityTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var productService: ProductService

    @MockitoBean
    lateinit var orderService: OrderService

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `user1 can create and update orders with order create scope`() {
        whenever(orderService.createOrder(any())).thenReturn(Id(1))

        postAsScope("/orders", "user1", "order:create", """{"productid":10,"count":2,"status":"pending","id":10}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))

        patchAsScope("/orders/10", "user1", "order:create", """{"productid":10,"count":1,"status":"pending","id":10}""")
            .andExpect(status().isOk)

        verify(orderService).createOrder(any())
        verify(orderService).updateOrder(any())
        verifyNoMoreInteractions(orderService)
    }

    @Test
    fun `user1 cannot create or update products without product create scope`() {
        postAsScope("/products", "user1", "order:create", """{"name":"Widget","type":"gadget","inventory":10}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        patchAsScope("/products/10", "user1", "order:create", """{"name":"Widget","type":"gadget","inventory":10,"id":10}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        verifyNoInteractions(productService)
    }

    @Test
    fun `service account can create and update products with product create scope`() {
        whenever(productService.addProduct(any())).thenReturn(Id(1))

        postAsScope("/products", "service_account", "product:create", """{"name":"Widget","type":"gadget","inventory":10,"id":10}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))

        patchAsScope("/products/10", "service_account", "product:create", """{"name":"Widget","type":"gadget","inventory":10,"id":10}""")
            .andExpect(status().isOk)

        verify(productService, times(2)).addProduct(any())
        verify(productService).updateProduct(any())
        verifyNoMoreInteractions(productService)
    }

    @Test
    fun `service account cannot create or update orders without order create scope`() {
        postAsScope("/orders", "service_account", "product:create", """{"productid":10,"count":2,"status":"pending"}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        patchAsScope("/orders/10", "service_account", "product:create", """{"productid":10,"count":1,"status":"pending","id":10}""")
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.error").value("Forbidden"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").exists())

        verifyNoInteractions(orderService)
    }

    @Test
    fun `health is public`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(content().string("OK"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/orders/10", "/products/10"])
    fun `POST no longer updates resources`(path: String) {
        mockMvc.perform(post(path))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Request method 'POST' is not supported"))
    }

    private fun postAsScope(path: String, subject: String, scope: String, body: String) =
        mockMvc.perform(
            post(path)
                .with(jwt().jwt { jwtBuilder ->
                    jwtBuilder.claim("sub", subject)
                    jwtBuilder.claim("scope", scope)
                }.authorities(SimpleGrantedAuthority("SCOPE_$scope")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )

    private fun patchAsScope(path: String, subject: String, scope: String, body: String) =
        mockMvc.perform(
            patch(path)
                .with(jwt().jwt { jwtBuilder ->
                    jwtBuilder.claim("sub", subject)
                    jwtBuilder.claim("scope", scope)
                }.authorities(SimpleGrantedAuthority("SCOPE_$scope")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
}
