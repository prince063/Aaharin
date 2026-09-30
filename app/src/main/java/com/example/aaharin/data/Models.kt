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
    /** Total of the bill raised on this table and not settled yet; 0 when there is none. */
    val billAmount: Double = 0.0,
    /** Number of that unsettled bill, for the settle screen. */
    val billNumber: String = "",
    /** item_id -> quantity for the running order, straight from table-list's cartData. */
    val itemQuantities: Map<Int, Int> = emptyMap(),
    /** Half portions on the running order: item_id -> (price key -> quantity). */
    val halfItemQuantities: Map<Int, Map<String, Int>> = emptyMap(),
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
    /**
     * The menu price already contains GST (`vaidi_items.is_inclusive_tax`).
     *
     * The server cascades this from a parent onto every one of its variations, so a
     * variant row carries its own answer and is never assumed to be exclusive.
     */
    val isInclusiveTax: Boolean = false,
    val hasHalfOption: Boolean = false,
    val halfPrice: Double? = null,
    val isFavourite: Boolean = false,
    /** Absolute URL of the item photo; empty when the item has none. */
    val imageUrl: String = "",
    /**
     * Label of this row as one of its parent's variations ("Half", "500ml", ...).
     *
     * Empty on a normal item, and also on a variation whose label the server could not
     * make more specific than the parent's own name.
     */
    val variantLabel: String = "",
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
    @SerializedName("occupied_capacity") val occupiedCapacity: Any?,
    /**
     * Bills raised on this table that nobody has settled - what makes it green.
     *
     * More than one only when the bill was split; the newest is first.
     */
    @SerializedName("awaiting_settlement") val awaitingSettlement: List<ApiAwaitingBill>? = null
)

/** One unsettled bill out of a table's `awaiting_settlement`. */
data class ApiAwaitingBill(
    @SerializedName("order_table_id") val orderTableId: Any? = null,
    @SerializedName("order_id") val orderId: String? = null,
    @SerializedName("billing_id") val billingId: Any? = null,
    @SerializedName("bill_no") val billNo: String? = null,
    @SerializedName("grand_total") val grandTotal: Any? = null,
    @SerializedName("discount") val discount: Any? = null
)

data class ApiMenuItem(
    @SerializedName("item_id", alternate = ["id", "item_id_pk"]) val id: Any?,
    @SerializedName("item_name", alternate = ["name", "product_name"]) val itemName: String?,
    @SerializedName("item_type", alternate = ["type", "veg_non_veg"]) val itemType: String?,
    @SerializedName("item_price", alternate = ["price", "amount"]) val itemPrice: Any?,
    // The server sends this already absolute (it builds it from BACKEND_UPLOAD_URL), and
    // as an empty string for items with no photo.
    @SerializedName("item_img", alternate = ["image", "item_image"]) val itemImg: String?,
    @SerializedName("category_id") val categoryId: Any?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("sub_category_id") val subCategoryId: Any?,
    @SerializedName("sub_category_name") val subCategoryName: String?,
    @SerializedName("is_favourite") val isFavourite: Any? = null,
    /** Set on rows inside [variations]; the server strips the "<parent> - " prefix. */
    @SerializedName("variation_label") val variationLabel: String? = null,
    /** 1 when the price already includes GST; sent on variation rows too. */
    @SerializedName("is_inclusive_tax") val isInclusiveTax: Any? = null,
    /** Whether a half portion can be ordered; the server resolves item vs category. */
    @SerializedName("allow_half") val allowHalf: Any? = null,
    /** Price of the half portion, already resolved by the server; null when not allowed. */
    @SerializedName("half_price") val halfPrice: Any? = null,
    /**
     * The item's sellable variations. A parent that has these is not orderable itself -
     * only one of its children can go on an order, which is how the web POS behaves.
     */
    @SerializedName("variations") val variations: List<ApiMenuItem>? = null
)

/**
 * Body for `item-list`.
 *
 * [itemSearch] is matched by the server against both `item_name` and `item_code` (and
 * against the same two columns on an item's variations), which is the only way to look an
 * item up by its code - the code itself is not part of the menu payload, so the device
 * cannot filter on it locally.
 *
 * Deliberately carries no `restaurant_id`: the controller answers a body that has one
 * without checking the access token at all, and takes the restaurant from whatever was
 * sent. Leaving it out keeps the request on the authenticated path, where the restaurant
 * comes from the token - which is also what this call did before it became a JSON body.
 */
data class ItemListRequest(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("table_id") val tableId: Int,
    @SerializedName("itemSearch") val itemSearch: String = ""
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
    /**
     * Half portions, as item_id -> (price key -> quantity) - the nested shape
     * Utility.php expects; a flat item_id -> quantity entry is ignored by the server.
     */
    @SerializedName("halfItemIdQtyArr") val halfItemIdQtyMap: Map<String, Map<String, Int>> = emptyMap(),
    @SerializedName("cgst") val cgst: Double = 2.5,
    @SerializedName("sgst") val sgst: Double = 2.5,
    @SerializedName("igst") val igst: Double = 0.0
)

/**
 * Read-only lookup of the running order's items through `item-list-data`.
 *
 * `user_id` is deliberately absent: getItemDataFromIds() only creates or updates an
 * order when BOTH restaurant_id and user_id are present, so leaving it out is what
 * keeps this call from writing anything. `confirm_order` makes the response carry
 * cartData as well, so live quantities come back alongside the prices.
 */
data class OrderItemsRequest(
    @SerializedName("item_ids") val itemIds: String,
    @SerializedName("table_id") val tableId: Int,
    @SerializedName("restaurant_id") val restaurantId: Int,
    @SerializedName("confirm_order") val confirmOrder: Int = 1
)

/**
 * Generates the bill through `item-list-data`, the same call the web POS makes from
 * ConfirmOrderController: `status = Completed` with an EMPTY `kot_print` is what makes
 * getItemDataFromIds() write the vaidi_order_billing record instead of a KOT. Sending a
 * non-empty kot_print here would print a kitchen ticket and never produce a bill.
 */
data class GenerateBillRequest(
    @SerializedName("item_ids") val itemIds: String,
    /**
     * MUST stay empty when billing. Every entry here is INSERTED as a new order item
     * (Utility.php:1700), so sending the order's existing lines duplicates all of them -
     * doubling the quantities and the bill. The billing branch recomputes the total from
     * the rows already saved against the order, so nothing is needed here. Only genuinely
     * new items added at billing time would belong in this map.
     */
    @SerializedName("itemIdQtyArr") val itemIdQtyMap: Map<String, Int> = emptyMap(),
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
