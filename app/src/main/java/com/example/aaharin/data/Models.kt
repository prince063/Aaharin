package com.example.aaharin.data

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class Table(
    val id: Int,
    val name: String = "Unknown",
    val status: TableStatus = TableStatus.VACANT,
    val serviceMode: String = "Dine-In",
    val serviceCategoryId: Int = 0,
    val orderId: String? = null,
    /** `vaidi_orders.id` - the numeric id the billing call keys off. */
    val orderTableId: Int = 0,
    val kotNumber: Int = 1,
    /** item_id -> quantity for the running order, straight from table-list's cartData. */
    val itemQuantities: Map<Int, Int> = emptyMap(),
    val itemsSummary: String? = null
)

enum class TableStatus {
    VACANT, OCCUPIED, BILL_PRINTED
}

data class Category(
    val id: Int,
    val name: String
)

data class MenuItem(
    val id: Int,
    val name: String,
    val price: Double,
    val categoryId: Int,
    val isVeg: Boolean = true,
    val hasHalfOption: Boolean = false,
    val halfPrice: Double? = null,
    val isFavourite: Boolean = false,
    val variants: List<MenuItem> = emptyList()
)

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int,
    val isHalf: Boolean = false,
    val notes: String = "",
    val status: ItemStatus = ItemStatus.PENDING
)

enum class ItemStatus {
    PENDING, COOKING, READY, DELIVERED
}

data class Kot(
    val id: Int,
    val tableId: Int,
    val tableName: String,
    val kotNumber: Int,
    val timestamp: String,
    val items: List<KotItem>,
    val isDone: Boolean = false
)

data class KotItem(
    val id: Int,
    val name: String,
    val quantity: Int,
    val isHalf: Boolean = false,
    val isPrepared: Boolean = false
)

enum class PaymentMode {
    CASH, UPI, CARD, CREDIT
}

data class ApiResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: T?,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("status") val statusStr: String? = null,
    @SerializedName("pdf_url") val pdfUrlStr: String? = null
)

data class LoginRequest(
    @SerializedName("mobile_number") val mobileNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("app_login_ip") val loginIp: String = "127.0.0.1"
)

data class LoginResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("role_id") val roleId: Int,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("mobile_number") val mobileNumber: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_exp") val tokenExp: Long,
    @SerializedName("restaurant") val restaurant: RestaurantData,
    @SerializedName("devices") val devices: List<DeviceData>?
)

data class RestaurantData(
    @SerializedName("restaurant_id", alternate = ["id"]) val id: Int,
    @SerializedName("restaurant_name", alternate = ["name"]) val name: String,
    @SerializedName("restaurant_address", alternate = ["address"]) val address: String?,
    @SerializedName("gst_no") val gstNo: String?,
    @SerializedName("upi") val upi: String?,
    // The billing call takes the tax rates from the client, not from the restaurant
    // record, so these have to be carried through or the bill total comes out wrong.
    // The API sends them as strings ("2.5").
    @SerializedName("cgst") val cgst: String? = null,
    @SerializedName("sgst") val sgst: String? = null,
    @SerializedName("igst") val igst: String? = null
)

data class DeviceData(
    @SerializedName("id") val id: Int,
    @SerializedName("device_type") val type: String?,
    @SerializedName("device_name") val name: String,
    @SerializedName("device_ip") val ip: String?
)

data class TableListResponse(
    @SerializedName("tableData") val tableData: List<ApiTableItem>,
    @SerializedName("cartData") val cartData: JsonElement?
)

data class ApiTableItem(
    @SerializedName("table_id", alternate = ["id", "id_table"]) val id: Any?,
    @SerializedName("table_name", alternate = ["mode_name", "name"]) val modeName: String?,
    @SerializedName("table_number", alternate = ["mode_number", "number"]) val modeNumber: String?,
    @SerializedName("service_category_id") val serviceCategoryId: Any?,
    @SerializedName("service_category_name") val serviceCategoryName: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("occupied_capacity") val occupiedCapacity: Any?
)

data class ApiMenuItem(
    @SerializedName("item_id", alternate = ["id", "item_id_pk"]) val id: Any?,
    @SerializedName("item_name", alternate = ["name", "product_name"]) val itemName: String?,
    @SerializedName("item_type", alternate = ["type", "veg_non_veg"]) val itemType: String?,
    @SerializedName("item_price", alternate = ["price", "amount"]) val itemPrice: Any?,
    @SerializedName("category_id") val categoryId: Any?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("sub_category_id") val subCategoryId: Any?,
    @SerializedName("sub_category_name") val subCategoryName: String?,
    @SerializedName("is_favourite") val isFavourite: Any? = null,
    @SerializedName("variations") val variations: List<ApiMenuItem>? = null
)

data class RefreshTokenRequest(
    @SerializedName("mobile_number") val mobileNumber: String
)

data class ServiceCategory(
    @SerializedName("service_category_id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category_code") val code: String = "",
    @SerializedName("facility_type") val facilityType: String = ""
)

data class RestaurantServicesResponse(
    @SerializedName("restaurant_id") val restaurantId: Int,
    @SerializedName("restaurant_name") val restaurantName: String,
    @SerializedName("services") val services: JsonElement 
)

data class OrderDetailsRequest(
    @SerializedName("table_id") val tableId: Int,
    @SerializedName("order_id") val orderId: String? = null
)

data class PlaceOrderRequest(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("items") val items: List<OrderItemRequest>,
    @SerializedName("table_id") val tableId: Int? = null,
    @SerializedName("access_token") val accessToken: String? = null
)

data class SaveKotRequest(
    @SerializedName("confirm_order") val confirmOrder: Int = 1,
    @SerializedName("kot_print") val kotPrint: String = "Yes",
    @SerializedName("status") val status: String = "Running",
    @SerializedName("restaurant_id") val restaurantId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("table_id") val tableId: Int,
    @SerializedName("order_table_id") val orderTableId: Int = 0,
    @SerializedName("kot_number") val kotNumber: Int = 1,
    @SerializedName("item_ids") val itemIds: String,
    @SerializedName("itemIdQtyArr") val itemIdQtyMap: Map<String, Int>,
    @SerializedName("cgst") val cgst: Double = 2.5,
    @SerializedName("sgst") val sgst: Double = 2.5,
    @SerializedName("igst") val igst: Double = 0.0
)

/**
 * Generates the bill through `item-list-data`, the same call the web POS makes from
 * ConfirmOrderController: `status = Completed` with an EMPTY `kot_print` is what makes
 * getItemDataFromIds() write the vaidi_order_billing record instead of a KOT. Sending a
 * non-empty kot_print here would print a kitchen ticket and never produce a bill.
 */
data class GenerateBillRequest(
    @SerializedName("item_ids") val itemIds: String,
    @SerializedName("itemIdQtyArr") val itemIdQtyMap: Map<String, Int>,
    @SerializedName("order_table_id") val orderTableId: Int,
    @SerializedName("kot_number") val kotNumber: Int = 1,
    @SerializedName("table_id") val tableId: Int,
    @SerializedName("restaurant_id") val restaurantId: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("status") val status: String = "Completed",
    /** Must stay empty - see the class comment. */
    @SerializedName("kot_print") val kotPrint: String = "",
    @SerializedName("cart_customer") val cartCustomer: Int = 1,
    // Tax rates are read from this payload, not from the restaurant row.
    @SerializedName("cgst") val cgst: Double = 2.5,
    @SerializedName("sgst") val sgst: Double = 2.5,
    @SerializedName("igst") val igst: Double = 0.0,
    @SerializedName("discount") val discount: Double = 0.0,
    @SerializedName("payment_type") val paymentType: String = "",
    @SerializedName("mobile_number") val mobileNumber: String = "",
    @SerializedName("customer_name") val customerName: String = "",
    @SerializedName("customer_mobile_number") val customerMobile: String = "",
    @SerializedName("customer_gst") val customerGst: String = "",
    @SerializedName("customer_address") val customerAddress: String = ""
)

data class OrderItemRequest(
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("quantity", alternate = ["item_count"]) val quantity: Int
)

data class OrderDetailWrapper(
    @SerializedName("new") val newItems: List<OrderItemRequest>
)

data class OrderResponse(
    @SerializedName("status") val status: String,
    @SerializedName("msg") val message: String?,
    @SerializedName("order_id") val orderId: Any?
)

data class PrintBillRequest(
    @SerializedName("table_id") val tableId: Int? = null,
    @SerializedName("order_id") val orderId: String? = null,
    @SerializedName("discount") val discount: Double = 0.0,
    @SerializedName("payment_type") val paymentType: String = "",
    @SerializedName("cust_name") val custName: String = "",
    @SerializedName("cust_mobile") val custMobile: String = "",
    @SerializedName("cust_email") val custEmail: String = "",
    @SerializedName("cust_gst") val custGst: String = "",
    @SerializedName("cust_address") val custAddress: String = ""
)

data class PrintBillResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("pdf_url") val pdfUrl: String?,
    @SerializedName("bill_html") val billHtml: String?,
    @SerializedName("whatsapp_text") val whatsappText: String?,
    @SerializedName("customer_mobile") val customerMobile: String?
)

data class SettleOrderRequest(
    @SerializedName("order_id") val orderId: String,
    @SerializedName("table_id") val tableId: Int? = null,
    @SerializedName("payment_mode") val paymentMode: String,
    @SerializedName("amount_paid") val amountPaid: Double
)
