package com.example.aaharin.network

import com.example.aaharin.data.*
import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    // 1. Staff Authentication
    @POST("login")
    suspend fun login(
        @Header("Access-Token") secretKey: String,
        @Header("access_token") secretKeyLowercase: String = secretKey,
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginResponse>>

    // 2. Refresh JWT Access Token
    @POST("refresh-token")
    suspend fun refreshToken(
        @Header("Access-Token") secretKey: String,
        @Header("access_token") secretKeyLowercase: String = secretKey,
        @Body request: RefreshTokenRequest
    ): Response<ApiResponse<JsonElement>>

    // 3. Fetch Restaurant Service Categories
    @GET("get-restaurant-services")
    suspend fun getRestaurantServices(
        @Header("Access-Token") headerToken: String,
        @Header("access_token") headerTokenLowercase: String = headerToken,
        @Query("access_token") queryToken: String = headerToken,
        @Query("restaurant_id") restaurantId: Int
    ): Response<ApiResponse<RestaurantServicesResponse>>

    // 4. Fetch Tables & Seating Status
    @FormUrlEncoded
    @POST("table-list")
    suspend fun getTableList(
        @Header("Access-Token") headerToken: String,
        @Header("access_token") headerTokenLowercase: String = headerToken,
        @Query("access_token") queryToken: String = headerToken,
        @Field("access_token") fieldToken: String = headerToken,
        @Field("restaurant_id") restaurantId: Int
    ): Response<ApiResponse<TableListResponse>>

    // 5. Fetch Menu Items Catalog
    @FormUrlEncoded
    @POST("item-list")
    suspend fun getItemList(
        @Header("Access-Token") headerToken: String,
        @Header("access_token") headerTokenLowercase: String = headerToken,
        @Query("access_token") queryToken: String = headerToken,
        @Field("access_token") fieldToken: String = headerToken,
        @Field("restaurant_id") restaurantId: Int,
        @Field("table_id") tableId: Int,
        @Field("itemSearch") itemSearch: String? = null
    ): Response<ApiResponse<JsonElement>>

    // 6. Menu Sync
    @FormUrlEncoded
    @POST("menu-sync")
    suspend fun menuSync(
        @Header("Access-Token") headerToken: String,
        @Header("access_token") headerTokenLowercase: String = headerToken,
        @Query("access_token") queryToken: String = headerToken,
        @Field("access_token") fieldToken: String = headerToken,
        @Field("restaurant_id") restaurantId: Int,
        @Field("service_id") serviceId: Int? = null
    ): Response<ApiResponse<JsonElement>>

    // 7. Save KOT / Item List Data
    @POST("item-list-data")
    suspend fun saveKot(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Body request: SaveKotRequest
    ): Response<ApiResponse<JsonElement>>

    // 7a. Read the running order's items
    //
    // Same endpoint again, but with no user_id, which is the guard that stops the server
    // creating or touching an order. Used to show an order placed anywhere else - the web
    // POS, a QR order - since there is no order-details route on the server.
    @POST("item-list-data")
    suspend fun getOrderItems(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Body request: OrderItemsRequest
    ): Response<JsonElement>

    // 7b. Generate Bill
    //
    // Same endpoint as saveKot, but with status=Completed and an empty kot_print, which
    // is what makes the server write the billing record rather than a kitchen ticket.
    // Returns the item list plus `order_already_placed`, which is 1 when this order was
    // already billed - the server stops there rather than issuing a second invoice.
    @POST("item-list-data")
    suspend fun generateBill(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Body request: GenerateBillRequest
    ): Response<JsonElement>

    // 8. Place New Order
    @POST("place-order")
    suspend fun placeOrder(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Body request: PlaceOrderRequest
    ): Response<ApiResponse<OrderResponse>>

    // 9. Get Order Details
    @POST("order-details")
    suspend fun getOrderDetails(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Body request: OrderDetailsRequest
    ): Response<JsonElement>

    // 10. Print Bill Form-Encoded
    @FormUrlEncoded
    @POST("print-bill")
    suspend fun printBillForm(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Field("table_id") tableId: Int,
        @Field("discount") discount: Double = 0.0,
        @Field("payment_type") paymentType: String = "",
        @Field("bill_printers") billPrinters: String = "[]",
        @Field("cust_name") custName: String = "",
        @Field("cust_mobile") custMobile: String = "",
        @Field("cust_email") custEmail: String = "",
        @Field("cust_gst") custGst: String = "",
        @Field("cust_address") custAddress: String = ""
    ): Response<PrintBillResponse>

    // 11. Settle Order
    @POST("settle-order")
    suspend fun settleOrder(
        @Header("access_token") accessToken: String,
        @Header("Access-Token") headerToken: String = accessToken,
        @Body request: SettleOrderRequest
    ): Response<ApiResponse<JsonElement>>
}
